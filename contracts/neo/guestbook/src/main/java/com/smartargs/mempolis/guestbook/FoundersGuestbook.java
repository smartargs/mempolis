package com.smartargs.mempolis.guestbook;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Hash160;
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
import io.neow3j.devpack.events.Event1Arg;
import io.neow3j.devpack.events.Event2Args;

/**
 * The founders' guestbook of one Mempolis city. A wallet signs once and gets the next founder number. The owner, set at
 * deploy, can close the book for good at the city's launch and can update the contract's code. The contract takes no
 * fee, holds no funds, calls nothing but the update itself and cannot be destroyed.
 */
@DisplayName("MempolisFoundersGuestbook")
@ContractSourceCode("https://github.com/smartargs/mempolis")
@ManifestExtra(key = "Author", value = "SmartArgs")
@ManifestExtra(key = "Source", value = "https://github.com/smartargs/mempolis")
@ManifestExtra(key = "License", value = "Apache-2.0")
@Permission(nativeContract = NativeContract.ContractManagement, methods = "update")
public class FoundersGuestbook {

    private static final int KEY_OWNER = 1;
    private static final int KEY_COUNT = 2;
    private static final int KEY_CLOSED = 3;
    private static final int PREFIX_NUMBER_OF = 4;
    private static final int PREFIX_FOUNDER_AT = 5;

    @DisplayName("Signed")
    @EventParameterNames({"wallet", "number"})
    private static Event2Args<Hash160, Integer> onSigned;

    @DisplayName("Closed")
    @EventParameterNames({"founders"})
    private static Event1Arg<Integer> onClosed;

    /**
     * Stores the owner on first deployment. An update leaves it unchanged.
     *
     * @param data the owner's script hash; a signing account, ideally a multi-sig
     * @param update whether this call comes from an update
     */
    @OnDeployment
    public static void deploy(Object data, boolean update) {
        if (update) return;
        if (data == null || !Hash160.isValid(data) || ((Hash160) data).isZero()) Helper.abort("GB: bad owner");
        Storage.put(Storage.getStorageContext(), KEY_OWNER, (Hash160) data);
    }

    /**
     * Signs the book for {@code wallet}, which must witness the transaction.
     *
     * @return the wallet's founder number, counted from 1
     */
    public static int sign(Hash160 wallet) {
        requireFirstCall();
        requireValidWallet(wallet);
        if (!Runtime.checkWitness(wallet)) Helper.abort("GB: no witness");
        if (!isOpen()) Helper.abort("GB: closed");

        StorageContext ctx = Storage.getStorageContext();
        StorageMap numbers = new StorageMap(ctx, PREFIX_NUMBER_OF);
        if (numbers.getIntOrZero(wallet) != 0) Helper.abort("GB: already signed");

        int number = Storage.getIntOrZero(ctx, KEY_COUNT) + 1;
        Storage.put(ctx, KEY_COUNT, number);
        numbers.put(wallet, number);
        new StorageMap(ctx, PREFIX_FOUNDER_AT).put(number, wallet);

        onSigned.fire(wallet, number);
        return number;
    }

    /** Closes the book for good. Owner only, and it cannot be reopened. */
    public static void close() {
        requireFirstCall();
        requireOwner();
        if (!isOpen()) Helper.abort("GB: already closed");

        Storage.put(Storage.getStorageContext(), KEY_CLOSED, 1);

        onClosed.fire(getCount());
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

    /** Whether wallets can still sign. */
    @Safe
    public static boolean isOpen() {
        return Storage.getIntOrZero(Storage.getReadOnlyContext(), KEY_CLOSED) == 0;
    }

    /** The account that can close the book and update the contract. */
    @Safe
    public static Hash160 getOwner() {
        return Storage.getHash160(Storage.getReadOnlyContext(), KEY_OWNER);
    }

    /** How many wallets have signed. */
    @Safe
    public static int getCount() {
        return Storage.getIntOrZero(Storage.getReadOnlyContext(), KEY_COUNT);
    }

    /** The founder number of {@code wallet}, or 0 if it never signed. */
    @Safe
    public static int numberOf(Hash160 wallet) {
        requireValidWallet(wallet);
        return new StorageMap(Storage.getReadOnlyContext(), PREFIX_NUMBER_OF).getIntOrZero(wallet);
    }

    /** The wallet holding founder {@code number}, or null if no wallet does. */
    @Safe
    public static Hash160 founderAt(int number) {
        return new StorageMap(Storage.getReadOnlyContext(), PREFIX_FOUNDER_AT).getHash160(number);
    }

    private static void requireFirstCall() {
        if (Runtime.getInvocationCounter() != 1) Helper.abort("GB: re-entry");
    }

    private static void requireOwner() {
        if (!Runtime.checkWitness(getOwner())) Helper.abort("GB: not owner");
    }

    private static void requireValidWallet(Hash160 wallet) {
        if (wallet == null || !Hash160.isValid(wallet) || wallet.isZero()) Helper.abort("GB: bad wallet");
    }
}
