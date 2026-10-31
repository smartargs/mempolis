package com.smartargs.mempolis.guestbook.testsupport;

import io.neow3j.devpack.Storage;
import io.neow3j.devpack.annotations.DisplayName;
import io.neow3j.devpack.annotations.Safe;

/** Test code only, never deployed: a later version of the guestbook, proving an update swaps code and keeps storage. */
@DisplayName("MempolisFoundersGuestbook")
public class UpdatedGuestbook {

    private static final int KEY_COUNT = 2;

    /** The version of this code. */
    @Safe
    public static int version() {
        return 2;
    }

    /** How many wallets signed, read from the storage the first version wrote. */
    @Safe
    public static int getCount() {
        return Storage.getIntOrZero(Storage.getReadOnlyContext(), KEY_COUNT);
    }
}
