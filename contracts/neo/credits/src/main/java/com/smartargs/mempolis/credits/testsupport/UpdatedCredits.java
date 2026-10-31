package com.smartargs.mempolis.credits.testsupport;

import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageMap;
import io.neow3j.devpack.annotations.DisplayName;
import io.neow3j.devpack.annotations.Safe;

/** Test code only, never deployed: a later version of the credits, proving an update swaps code and keeps storage. */
@DisplayName("MempolisCityCredits")
public class UpdatedCredits {

    private static final int PREFIX_BALANCE = 4;

    /** The version of this code. */
    @Safe
    public static int version() {
        return 2;
    }

    /** A balance, read from the storage the first version wrote. */
    @Safe
    public static int balanceOf(Hash160 wallet) {
        return new StorageMap(Storage.getReadOnlyContext(), PREFIX_BALANCE).getIntOrZero(wallet);
    }
}
