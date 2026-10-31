package com.smartargs.mempolis.credits;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Hash256;
import io.neow3j.devpack.Helper;
import io.neow3j.devpack.Runtime;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.StorageMap;
import io.neow3j.devpack.annotations.ContractSourceCode;
import io.neow3j.devpack.annotations.DisplayName;
import io.neow3j.devpack.annotations.EventParameterNames;
import io.neow3j.devpack.annotations.ManifestExtra;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.constants.NativeContract;
import io.neow3j.devpack.contracts.ContractManagement;
import io.neow3j.devpack.contracts.CryptoLib;
import io.neow3j.devpack.events.Event2Args;
import io.neow3j.devpack.events.Event3Args;

/**
 * The city credits of one Mempolis city: a soft currency earned by staying active. Credits cannot be sent or traded,
 * so the contract offers no transfer and claims no token standard. The owner posts a Merkle root of each wallet's
 * lifetime earned total, and a wallet claims what it has not claimed yet. Spending arrives with an update, once the
 * game has something to spend on. No method lets the owner touch a balance, though it can update the contract's code.
 * The contract takes no fee, holds no funds and cannot be destroyed.
 */
@DisplayName("MempolisCityCredits")
@ContractSourceCode("https://github.com/smartargs/mempolis")
@ManifestExtra(key = "Author", value = "SmartArgs")
@ManifestExtra(key = "Source", value = "https://github.com/smartargs/mempolis")
@ManifestExtra(key = "License", value = "Apache-2.0")
@Permission(nativeContract = NativeContract.ContractManagement, methods = "update")
@Permission(nativeContract = NativeContract.CryptoLib, methods = "sha256")
public class CityCredits {

    private static final int KEY_OWNER = 1;
    private static final int KEY_EPOCH = 2;
    private static final int KEY_ROOT = 3;
    private static final int PREFIX_BALANCE = 4;

    private static final byte TAG_LEAF = 0;
    private static final byte TAG_BRANCH = 1;
    private static final int HASH_LENGTH = 32;
    private static final int MAX_PROOF_LENGTH = 32;

    @DisplayName("RootPosted")
    @EventParameterNames({"epoch", "root"})
    private static Event2Args<Integer, ByteString> onRootPosted;

    @DisplayName("Claimed")
    @EventParameterNames({"wallet", "amount", "earned"})
    private static Event3Args<Hash160, Integer, Integer> onClaimed;

    /**
     * Stores the owner on first deployment. An update leaves it unchanged.
     *
     * @param data the owner's script hash; a signing account
     * @param update whether this call comes from an update
     */
    @OnDeployment
    public static void deploy(Object data, boolean update) {
        if (update) return;
        if (data == null || !Hash160.isValid(data) || ((Hash160) data).isZero()) Helper.abort("CC: bad owner");
        Storage.put(Storage.getStorageContext(), KEY_OWNER, (Hash160) data);
    }

    /**
     * Posts the Merkle root of every wallet's lifetime earned total. Owner only. It replaces the previous root, so
     * proofs are only valid until the next one.
     *
     * @param epoch the next epoch, one above {@link #getEpoch()}
     * @param root the 32-byte root
     */
    public static void postRoot(int epoch, ByteString root) {
        requireFirstCall();
        requireOwner();
        if (epoch != getEpoch() + 1) Helper.abort("CC: bad epoch");
        if (root == null || !Hash256.isValid(root) || new Hash256(root).isZero()) Helper.abort("CC: bad root");

        StorageContext ctx = Storage.getStorageContext();
        Storage.put(ctx, KEY_EPOCH, epoch);
        Storage.put(ctx, KEY_ROOT, root);

        onRootPosted.fire(epoch, root);
    }

    /**
     * Credits {@code wallet} with what it earned since its last claim. The wallet must witness the transaction.
     *
     * @param wallet the wallet claiming
     * @param earned the wallet's lifetime earned total in the current root
     * @param index the leaf's position in the tree
     * @param proof the sibling hashes from the leaf up to the root
     * @return the amount credited
     */
    public static int claim(Hash160 wallet, int earned, int index, ByteString[] proof) {
        requireFirstCall();
        requireWitness(wallet);
        requireInCurrentRoot(wallet, earned, index, proof);

        StorageMap balances = new StorageMap(Storage.getStorageContext(), PREFIX_BALANCE);
        int amount = earned - balances.getIntOrZero(wallet);
        if (amount <= 0) Helper.abort("CC: nothing to claim");

        balances.put(wallet, earned);

        onClaimed.fire(wallet, amount, earned);
        return amount;
    }

    /**
     * Replaces the contract's code and manifest and keeps its storage. Owner only.
     *
     * @param nef the new NEF file
     * @param manifest the new manifest as JSON, with the same contract name
     * @param data handed to the new code's deploy callback
     */
    public static void update(ByteString nef, String manifest, Object data) {
        requireFirstCall();
        requireOwner();
        new ContractManagement().update(nef, manifest, data);
    }

    /** The account that posts roots and can update the contract. */
    @Safe
    public static Hash160 getOwner() {
        return Storage.getHash160(Storage.getReadOnlyContext(), KEY_OWNER);
    }

    /** How many roots have been posted. */
    @Safe
    public static int getEpoch() {
        return Storage.getIntOrZero(Storage.getReadOnlyContext(), KEY_EPOCH);
    }

    /** The current Merkle root, or null before the first one. */
    @Safe
    public static ByteString getRoot() {
        return Storage.get(Storage.getReadOnlyContext(), KEY_ROOT);
    }

    /** The credits {@code wallet} has claimed. */
    @Safe
    public static int balanceOf(Hash160 wallet) {
        requireValidWallet(wallet);
        return new StorageMap(Storage.getReadOnlyContext(), PREFIX_BALANCE).getIntOrZero(wallet);
    }

    private static void requireFirstCall() {
        if (Runtime.getInvocationCounter() != 1) Helper.abort("CC: re-entry");
    }

    private static void requireOwner() {
        if (!Runtime.checkWitness(getOwner())) Helper.abort("CC: not owner");
    }

    private static void requireWitness(Hash160 wallet) {
        requireValidWallet(wallet);
        if (!Runtime.checkWitness(wallet)) Helper.abort("CC: no witness");
    }

    private static void requireValidWallet(Hash160 wallet) {
        if (wallet == null || !Hash160.isValid(wallet) || wallet.isZero()) Helper.abort("CC: bad wallet");
    }

    private static void requireInCurrentRoot(Hash160 wallet, int earned, int index, ByteString[] proof) {
        if (index < 0 || proof.length > MAX_PROOF_LENGTH) Helper.abort("CC: bad proof");

        ByteString node = hash(tagged(TAG_LEAF).concat(wallet.toByteString()).concat(new ByteString(earned)));
        for (int i = 0; i < proof.length; i++) {
            if (proof[i].length() != HASH_LENGTH) Helper.abort("CC: bad proof");
            node = (index & 1) == 0 ? branch(node, proof[i]) : branch(proof[i], node);
            index = index >> 1;
        }

        ByteString root = getRoot();
        if (root == null || !node.equals(root)) Helper.abort("CC: bad proof");
    }

    private static ByteString branch(ByteString left, ByteString right) {
        return hash(tagged(TAG_BRANCH).concat(left).concat(right));
    }

    private static ByteString tagged(byte tag) {
        return new ByteString(new byte[]{tag});
    }

    private static ByteString hash(ByteString value) {
        return new CryptoLib().sha256(value);
    }
}
