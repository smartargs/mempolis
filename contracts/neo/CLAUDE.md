# Working on the Neo N3 contracts

neow3j contracts, one Gradle module per contract, with shared test code in `testkit`. Run Gradle with `JAVA_HOME` set to JDK 21 (Gradle 8.7 does not run on 25). `./gradlew test` needs Docker.

## Design

- **Owner updates, nothing else.** Each contract has an owner-only `update(nef, manifest, data)`. No `destroy`, no pause. `_deploy` returns early when `update` is true, so an update can never reset the owner.
- **One privileged role at most**, set at deploy. Give it the smallest possible power and say in the class Javadoc what it can do. Never add a method that touches a holder's balance or number.
- **Least authority.** List only the `@Permission` entries that are used, naming the native contract and the method, as narrowly as it works.
- **No funds unless that is the contract's job.** Without an `@OnNEP17Payment` method, transfers fault instead of stranding. When a contract does take payments, check the calling token's hash, and handle the `from == null` case.
- **The contract's value must not hang on grindable randomness.** `Runtime.getRandom()` can be ground by a transaction script.

## Writing a contract

- Take the principal as an explicit parameter and `Runtime.checkWitness` it. Never infer it from `Runtime.getCallingScriptHash()`.
- Every `Hash160` argument: `x == null || !Hash160.isValid(x) || x.isZero()` aborts. Check null first, because `isValid(null)` faults inside the VM and loses your message. This includes deploy data, which arrives as `Object`, and read methods.
- Guard every state-changing method with `Runtime.getInvocationCounter() != 1`.
- Validate everything, then write state, then fire the event, then call out. Never call out before state is final.
- Revert with `Helper.abort("XX: reason")`, using one short prefix per contract. Never Java `assert` or `throw`: they are catchable and cost more.
- Read methods are `@Safe` and use `Storage.getReadOnlyContext()`.
- Statics run on every invocation and cost gas. Keys and prefixes are inlined `static final int` constants; build `StorageContext` and `StorageMap` inside the method that needs them.
- Storage prefixes are single bytes and none is a prefix of another. Composite keys are fixed width.
- Never collect unbounded storage into an array. Page it or return an iterator.
- Cap the length of any stored string; front-ends treat it as untrusted.
- Set `@ContractSourceCode` and the `Author`, `Source` and `License` manifest extras.
- Comments follow the root rule: Javadoc on the public API only.

## What the compiler does not tell you

- `src/main` is Java 8 bytecode, `src/test` is 17. Do not tidy the split in the root `build.gradle.kts`.
- `compileJava` passing does not mean it compiles to NeoVM. Run `neow3jCompile`.
- `int` is arbitrary precision at runtime. There are no floats and no wrap-around.
- `new` on a `@Struct` leaves its fields VM-null. Set every field. Append struct fields, never reorder them.
- Cast a deserialized array to `Object[]`. Do not cast a value returned by `Contract.call` to a struct.
- Native contracts are instances (`new StdLib()`), not static classes.
- The contract hash depends on the sender, the NEF checksum and the manifest name, so renaming changes the address.
- `neow3jVersion` lives in `gradle.properties` and drives both the plugin and the libraries. Bumping it changes the NEF and can change the test node image, so re-run all tests.
- `Runtime.checkWitness` with `CalledByEntry` does not reach a contract called by another contract. A forwarding contract therefore cannot act for the wallet that signed the call to it.
- A `@ContractTest` needs `configFile = TestHelper.NODE_CONFIG`, or the Neo 3.10 node refuses the deploy with `Insufficient GAS`.
- Build the extension with `new ContractTestExtension(new ReadyNeoExpressContainer())`. The node's port can open before its RPC methods work, and a warm test JVM then fails on its first call with "The given key was not present in the dictionary".

## Tests

- Every reachable `Helper.abort` has a test, named as a one-line spec. Test the attack as well as the error: a forged witness, a wrong-shaped argument, a repeated call, a contract that lies.
- Reverts are not exceptions. Call `neow3j.allowTransmissionOnFault()` in `@BeforeAll` and use `TestHelper.assertAborted`.
- All tests in a class share one chain. Put irreversible states in their own test class, or deploy a fresh instance with `TestHelper.deploy`, and do not assume absolute counts.
- Contracts listed in `@ContractTest` need Java 8 bytecode, so helper contracts live in `src/main` under a `testsupport` package and are never deployed.
- Prove an update with a later version in `testsupport`: the code changes, the storage stays, and data passed to `update` cannot change the owner.
- A transaction mines about one block after submit. Read time-dependent state after it confirms.

## Before mainnet

Pin the commit, check that two clean builds give the same NEF hash, deploy to testnet first, use a multi-sig owner, because it can replace the code, and get a review of anything holding value.

Sources: neow3j docs at <https://neow3j.io>, and the checklist at <https://github.com/smartargs/neo-sc-audits/blob/main/BEST_PRACTICES.md>.
