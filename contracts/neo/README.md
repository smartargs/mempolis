# Neo N3 contracts

The Mempolis contracts for Neo N3, written in Java with [neow3j](https://neow3j.io/). Each contract is one Gradle module. EVM chains such as Neo X get their own folder next to this one.

| Module | What it does |
| --- | --- |
| [`guestbook`](guestbook) | The founders' guestbook: a wallet signs once and gets its founder number |
| [`credits`](credits) | City credits: a soft currency earned by staying active, which cannot be transferred |
| [`deed`](deed) | Deeds: one soulbound NEP-11 token per wallet for its home on a lot of the city's plan |
| [`testkit`](testkit) | Test helpers and the test node's config, shared by the contract modules |
| [`deploy`](deploy) | The deploy tool behind `deploy.sh` |

## Build and test

Use JDK 21. Gradle 8.7 does not run on JDK 25, and the NEF is only verified as reproducible on 21.

```sh
export JAVA_HOME=/path/to/jdk-21
./gradlew test             # needs a running Docker: the tests use a real Neo node
./gradlew neow3jCompile    # writes the .nef and manifest to <module>/build/neow3j
```

`testkit/src/main/resources/test.neo-express` is the neow3j default config with `rpc.MaxGasInvoke` raised. Without it, deploying on the Neo 3.10 test node fails with `Insufficient GAS`. Its keys are neow3j's public test keys. `ReadyNeoExpressContainer` waits for the node's RPC methods to answer, because its port can open first.

Classes in a `testsupport` package are test code. They sit in `src/main` because the test node needs them as Java 8 bytecode, and they are never deployed.

## Deploy

```sh
./deploy.sh testnet guestbook --gate <key> --dry-run             # shows addresses and cost, sends nothing
./deploy.sh testnet guestbook --gate <key> --owner <address>     # the owner defaults to the deployer on testnet
./deploy.sh mainnet guestbook --gate <key> --owner <owner address>
```

Name `credits` or `deed`, or `all` for every contract, and use `--rpc <url>` to pick another node. `--gate` is the public key whose vouchers the guestbook and the deed contract accept. The deed contract also needs `--treasury <address>`, where payments go, and reads founders from the guestbook recorded for that network unless `--guestbook <hash>` names another; `--styles <n>` sets how many house styles it starts with (4 by default). The script finds JDK 21 by itself or uses `JAVA_HOME`, and always builds first.

The key comes from `NEO_DEPLOYER_WIF`, which `deploy.sh` reads from a git-ignored `.env` next to it, or from a hidden prompt. It only signs and pays for the deployment. The contract's owner is whatever `--owner` names, and that account does not have to sign. A deployment costs about 10 GAS per contract.

The deployer key is part of the contract's address, along with the NEF checksum and the contract name. Use the same key to get the same address, and note that changed code deploys to a new address with empty storage. To change a live contract, the owner calls `update` instead.

A contract that is already on chain is checked and skipped, so a run that was interrupted can simply be repeated. Each deployment is recorded in `deployments/<network>.json`. Commit those files.

`deploy.sh mainnet` refuses to continue unless all of these hold:

- The tree is clean, apart from `deployments/`.
- `./gradlew test` passes.
- Two clean builds give identical artifacts.
- This exact build is recorded in `deployments/testnet.json`.
- `--owner` is named and is not the deploying key.
- You type `mainnet` to confirm.

`./deploy.sh setup <testnet|mainnet> --plan <file>` loads a city's lot registry and free queue onto its deed contract, signed by the owner key in `NEO_OWNER_WIF` (or a hidden prompt). The plan file is JSON with `lots`, one registry byte per lot from lot 1, and `queue`, lot numbers in the order free claims get them. It sends only what the chain lacks yet, in batches of 200, so an interrupted run can be repeated; it refuses a plan that disagrees with what is registered, and checks every lot and queue place at the end. `--dry-run` shows what is missing. A multi-sig owner has to sign these calls another way.

`./deploy.sh wallet <name>` creates a new wallet, writing its key to `~/mempolis-keys/<name>.wif`, readable only by you, and printing its address.

The owner can replace a contract's code, so keep it apart from everyday use. Move it to a multi-sig before anything of value depends on it; that takes an update, because there is no method to change the owner.

## The founders' guestbook

| Method | Who | What |
| --- | --- | --- |
| `sign(wallet, voucher)` | The wallet | Records the next founder number, once per wallet, and returns it |
| `setGate(gate)` | Owner | Replaces the key vouchers must come from |
| `close()` | Owner | Closes the book for good |
| `update(nef, manifest, data)` | Owner | Replaces the code and keeps the storage |
| `isOpen()`, `getOwner()`, `getGate()`, `getCount()` | Anyone | State of the book |
| `numberOf(wallet)` | Anyone | The wallet's founder number, 0 if it never signed |
| `founderAt(number)` | Anyone | The wallet holding a number, null if none does |

Each city has its own guestbook and its own numbers. `Signed(wallet, number)`, `Closed(founders)` and `GateChanged(gate)` are emitted as events.

**Vouchers.** A wallet needs a voucher to sign, so a script cannot fill the book from fresh wallets. A voucher is an ECDSA signature (secp256r1 with SHA-256) by the gate key over the contract's script hash followed by the wallet's, both little-endian, as 64 bytes of r and s in base64. It travels as a string because wallets disagree on how to encode byte arguments. The project gives a voucher to wallets that pass its anti-bot check.

## City credits

Credits are earned off chain and proven on chain. Each week the owner posts a Merkle root of every wallet's lifetime earned total, and a wallet claims the difference from what it claimed before.

| Method | Who | What |
| --- | --- | --- |
| `postRoot(epoch, root)` | Owner | Posts the next root, replacing the last one |
| `claim(wallet, earned, index, proof)` | The wallet | Credits what was earned since its last claim |
| `update(nef, manifest, data)` | Owner | Replaces the code and keeps the storage |
| `getOwner()`, `getEpoch()`, `getRoot()` | Anyone | State of the contract |
| `balanceOf(wallet)` | Anyone | The credits the wallet has claimed |

There is no transfer and no token standard, so wallets do not list credits. There is no spending yet either: it arrives with an update, once the game has something to spend on. A wallet's balance is the lifetime total it has claimed, so nothing can be claimed twice, while later earnings stay claimable.

**The tree.** A leaf is `sha256(0x00 || wallet || earned)` and a branch is `sha256(0x01 || left || right)`, so a branch can never pass as a leaf. `wallet` is the little-endian script hash, and `earned` is the minimal little-endian two's-complement integer, as NeoVM converts it. Leaves are padded to a power of two with 32 zero bytes. `index` is the leaf's position, and its bits choose left or right at each level. Proofs hold at most 32 hashes.

## Trust model

- **Two privileged keys in the guestbook.** The owner is set at deploy, and both contracts keep the same owner across updates unless new code changes it. The guestbook also has a gate key, held on an online server. It can only approve wallets to sign: it cannot sign for a wallet, change a number, close the book or update the code. The project therefore decides who may sign. A stolen gate key lets anyone approve wallets until the owner replaces it with `setGate`.
- **The owner can update the code.** An update keeps storage and can change any behaviour, so every guarantee below describes the current code, not a permanent promise. Anyone can see an update on chain: the contract's update counter rises and its NEF hash changes.
- **No method touches a balance or a founder number.** With the current code the owner cannot reopen a closed book, renumber a founder, or change a wallet's credits.
- **The owner decides what is earned.** A wrong or dishonest root can over-credit wallets, and it cannot be taken back. Anyone can rebuild the tree from public data and compare roots.
- **No funds.** There is no fee and no payment callback, so a token transfer to either contract faults instead of stranding.
- **Narrow reach.** Each manifest grants only what is used: `ContractManagement.update`, for the guestbook `CryptoLib.verifyWithECDsa` and `StdLib.base64Decode`, and for credits `CryptoLib.sha256`.
- **No destroy.** Neither contract can delete itself.

## Known limitations

- **A lost owner key freezes the owner's actions.** The book can no longer close, roots stop and updates stop. Founders keep their numbers, and wallets can still claim against the last root. Keep the owner key offline and backed up, and use a multi-sig before anything of value depends on it.
- **A stolen owner key can replace the code.** This is the cost of updates, and the reason to guard the owner key.
- **State-changing methods are limited to one call into the contract per transaction.** A script that reads a contract and then writes to it in the same transaction aborts.
- **A proof is only valid until the next root.** A claim sent just as a new root lands fails and has to be repeated with a new proof.
- **A signature proves control of a wallet, not who is behind it.** One person can sign from several wallets that pass the anti-bot check.
- **Signing depends on the voucher service.** While it is down nobody can sign. Founders already signed are not affected.
- **Storage grows with every wallet.** That is by design: each wallet pays for its own entries.

## Verify a deployment

The addresses are in `deployments/<network>.json`. Check out the tag the deployer names, compile with the same `neow3jVersion` from `gradle.properties` and JDK 21, then compare the NEF and manifest with what `getcontractstate` returns for the contract. If either differs, the deployed contract is not this source. Check again after any update.

## Deeds

Claiming a plot mints one Deed per wallet. It cannot be transferred: `transfer` always aborts. The token id is the claim number. The city's lots are registered append-only, one byte each: the tier (1 standard, 2 choice, 3 prime, 4 VIP) plus 8 times the ring from the centre. A registered lot never changes.

| Method | Who | What |
| --- | --- | --- |
| `claim(wallet, voucher)` | The wallet | Free claim: the next lot of the queue in the free house style; needs a voucher unless the wallet is a founder |
| GAS transfer with `[1]` | The wallet | Newcomer claim: the same, for the newcomer fee |
| GAS transfer with `[2, lot, style]` | The wallet | Chosen claim: a free choice or prime lot inside the open rings, in any house style, for its tier's price |
| GAS transfer with `[3, style]` | The holder | Changes the house style, for the restyle price |
| `setLook(wallet, look)`, `setStatus(wallet, status)` | The holder | The resident's look (1 to 16 bytes) and speech-bubble line (up to 112 bytes) |
| `registerLots(first, infos)`, `queueLots(first, lots)` | Owner | Appends lots to the registry and standard lots to the free queue |
| `setPrice(kind, amount)` | Owner | Newcomer fee (1), choice (2), prime (3) or restyle (4) price in GAS fractions; 0 stops that sale |
| `openRingUpTo(ring)`, `setPhase(phase)`, `setStyleCount(n)` | Owner | Opens rings outward, moves from closed (0) to founders (1) to open (2), adds house styles; none of them goes back |
| `setGate(gate)`, `setTreasury(treasury)`, `update(nef, manifest, data)` | Owner | Replaces the voucher key, the treasury, or the code |
| `numberOf`, `deedAt`, `deeds(from, count)`, `tierOf`, `ringOf`, `claimOn`, `nextFreeLot`, `queueAt`, `priceOf`, ... | Anyone | State of the city, plus the NEP-11 reads |

- **Phases.** The founders phase needs the guestbook closed, and in it only wallets with a founder number can claim. Founders claim free without a voucher.
- **No randomness at claim time.** The free queue is a published order of the standard lots, ring by ring from the centre, so a claim cannot be ground for a better lot. A free claim whose lot lies in the next ring opens that ring.
- **Payments.** Every payment must come from GAS and match its price exactly, and it is forwarded to the treasury in the same transaction, so the contract never holds funds. VIP lots are reserved and cannot be bought.
- **Vouchers** are signed like the guestbook's, over the deed contract's own hash and the wallet, followed by the network's magic number as a NeoVM integer, so neither a guestbook voucher nor one for another network can claim.
- **Payment data** values are turned into plain integers before use, so a lot sent as raw bytes cannot slip past the taken-lot check.
- **The free queue** skips a lot that is already taken when its place comes up, so a lot queued twice is handed out once.

Events: NEP-11 `Transfer` on mint, `Claimed(wallet, number, lot, style)`, `StyleChanged`, `LookChanged`, `StatusChanged`, `LotsRegistered`, `LotsQueued`, `PriceChanged`, `RingOpened`, `PhaseChanged`, `StylesChanged`, `TreasuryChanged` and `GateChanged`.

Lots and queue places are stored in chunks of 200 bytes, so registering the Sprawl's 1,083 lots and 737 queue places costs about 10 GAS. `tokens()` iterates in storage key order, which is not claim order; use `deeds(from, count)` to read the city in claim order.
