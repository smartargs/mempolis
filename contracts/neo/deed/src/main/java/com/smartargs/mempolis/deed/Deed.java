package com.smartargs.mempolis.deed;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.annotations.Struct;

/** One claimed plot, as the read methods return it. Fields are only ever appended. */
@Struct
public class Deed {

    /** The claim number, counted from 1; also the token id. */
    public int number;

    /** The wallet that claimed it. */
    public Hash160 wallet;

    /** The lot number in the city's plan. */
    public int lot;

    /** The house style, counted from 0, where 0 is the city's free style. */
    public int style;

    /** The founder number from the guestbook, or 0. */
    public int founder;

    /** The resident's look, or null until the owner creates a resident. */
    public ByteString look;

    /** The speech-bubble line, or null. Untrusted text. */
    public String status;

    /** A deed with every field set. */
    public Deed(int number, Hash160 wallet, int lot, int style, int founder, ByteString look, String status) {
        this.number = number;
        this.wallet = wallet;
        this.lot = lot;
        this.style = style;
        this.founder = founder;
        this.look = look;
        this.status = status;
    }
}
