# Neo N3 contracts

The Mempolis contracts for Neo N3, written in Java with [neow3j](https://neow3j.io/). Each contract is one Gradle module. EVM chains such as Neo X get their own folder next to this one.

| Module | What it does |
| --- | --- |
| [`guestbook`](guestbook) | The founders' guestbook: a wallet signs once and gets its founder number |
| [`credits`](credits) | City credits: a soft currency earned by staying active, which cannot be transferred |
| [`testkit`](testkit) | Test helpers and the test node's config, shared by the contract modules |

## Build and test

Use JDK 21. Gradle 8.7 does not run on JDK 25, and the NEF is only verified as reproducible on 21.

```sh
export JAVA_HOME=/path/to/jdk-21
./gradlew test             # needs a running Docker: the tests use a real Neo node
./gradlew neow3jCompile    # writes the .nef and manifest to <module>/build/neow3j
```

`testkit/src/main/resources/test.neo-express` is the neow3j default config with `rpc.MaxGasInvoke` raised. Without it, deploying on the Neo 3.10 test node fails with `Insufficient GAS`. Its keys are neow3j's public test keys. `ReadyNeoExpressContainer` waits for the node's RPC methods to answer, because its port can open first.

Classes in a `testsupport` package are test code. They sit in `src/main` because the test node needs them as Java 8 bytecode, and they are never deployed.

## The founders' guestbook

| Method | Who | What |
| --- | --- | --- |
| `sign(wallet)` | The wallet | Records the next founder number, once per wallet, and returns it |
| `close()` | Owner | Closes the book for good |
| `update(nef, manifest, data)` | Owner | Replaces the code and keeps the storage |
| `isOpen()`, `getOwner()`, `getCount()` | Anyone | State of the book |
| `numberOf(wallet)` | Anyone | The wallet's founder number, 0 if it never signed |
| `founderAt(number)` | Anyone | The wallet holding a number, null if none does |

Each city has its own guestbook and its own numbers. `Signed(wallet, number)` and `Closed(founders)` are emitted as events.

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

- **One privileged role.** The owner is set at deploy. Both contracts keep the same owner across updates unless new code changes it.
- **The owner can update the code.** An update keeps storage and can change any behaviour, so every guarantee below describes the current code, not a permanent promise. Anyone can see an update on chain: the contract's update counter rises and its NEF hash changes.
- **No method touches a balance or a founder number.** With the current code the owner cannot reopen a closed book, renumber a founder, or change a wallet's credits.
- **The owner decides what is earned.** A wrong or dishonest root can over-credit wallets, and it cannot be taken back. Anyone can rebuild the tree from public data and compare roots.
- **No funds.** There is no fee and no payment callback, so a token transfer to either contract faults instead of stranding.
- **Narrow reach.** Each manifest grants only what is used: `ContractManagement.update`, and for credits `CryptoLib.sha256`.
- **No destroy.** Neither contract can delete itself.

## Known limitations

- **A lost owner key freezes the owner's actions.** The book can no longer close, roots stop and updates stop. Founders keep their numbers, and wallets can still claim against the last root. Use a multi-sig owner for anything mainnet.
- **A stolen owner key can replace the code.** This is the cost of updates, and it is the main reason to use a multi-sig.
- **State-changing methods are limited to one call into the contract per transaction.** A script that reads a contract and then writes to it in the same transaction aborts.
- **A proof is only valid until the next root.** A claim sent just as a new root lands fails and has to be repeated with a new proof.
- **A signature proves control of a wallet, not who is behind it.** One person can sign from several wallets.
- **Storage grows with every wallet.** That is by design: each wallet pays for its own entries.

## Verify a deployment

Check out the tag the deployer names, compile with the same `neow3jVersion` from `gradle.properties` and JDK 21, then compare the NEF and manifest with what `getcontractstate` returns for the contract. If either differs, the deployed contract is not this source. Check again after any update.
