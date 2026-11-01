package com.smartargs.mempolis.deploy;

import java.util.Arrays;

/** A public Neo N3 network and the node used to reach it unless another is named. */
public enum Network {

    TESTNET("testnet", 894710606L, "https://testnet1.neo.coz.io:443"),
    MAINNET("mainnet", 860833102L, "https://mainnet1.neo.coz.io:443");

    private final String label;
    private final long magic;
    private final String defaultRpc;

    Network(String label, long magic, String defaultRpc) {
        this.label = label;
        this.magic = magic;
        this.defaultRpc = defaultRpc;
    }

    /** The network called {@code label}. */
    public static Network named(String label) {
        return Arrays.stream(values())
                .filter(network -> network.label.equals(label))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown network: " + label));
    }

    /** The name used on the command line and for the network's deployment record. */
    public String label() {
        return label;
    }

    /** The magic number a node of this network reports. */
    public long magic() {
        return magic;
    }

    /** The public node used by default. */
    public String defaultRpc() {
        return defaultRpc;
    }
}
