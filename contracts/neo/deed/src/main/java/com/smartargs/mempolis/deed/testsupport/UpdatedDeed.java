package com.smartargs.mempolis.deed.testsupport;

import io.neow3j.devpack.Storage;
import io.neow3j.devpack.annotations.DisplayName;
import io.neow3j.devpack.annotations.Safe;

/** Test code only, never deployed: a later version of the deed contract, proving an update swaps code and keeps storage. */
@DisplayName("MempolisDeed")
public class UpdatedDeed {

    private static final int KEY_LOTS = 8;

    /** The version of this code. */
    @Safe
    public static int version() {
        return 2;
    }

    /** How many lots are registered, read from the storage the first version wrote. */
    @Safe
    public static int lotCount() {
        return Storage.getIntOrZero(Storage.getReadOnlyContext(), KEY_LOTS);
    }
}
