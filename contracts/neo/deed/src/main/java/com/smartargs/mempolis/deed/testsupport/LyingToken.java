package com.smartargs.mempolis.deed.testsupport;

import io.neow3j.devpack.Contract;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.annotations.DisplayName;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.constants.CallFlags;

/** Test code only, never deployed: a token that claims a payment it never made. */
@DisplayName("LyingToken")
@Permission(contract = "*", methods = "onNEP17Payment")
public class LyingToken {

    /** Tells {@code target} that {@code from} paid it {@code amount}, without moving anything. */
    public static void pretend(Hash160 target, Hash160 from, int amount, Object data) {
        Contract.call(target, "onNEP17Payment", CallFlags.All, new Object[]{from, amount, data});
    }
}
