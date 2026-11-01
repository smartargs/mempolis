package com.smartargs.mempolis.deploy;

import io.neow3j.crypto.ECKeyPair.ECPublicKey;
import io.neow3j.types.Hash160;

import java.util.List;
import java.util.Optional;

/**
 * What one run of the deploy tool was asked to do.
 *
 * @param network the network to deploy to
 * @param modules the Gradle modules whose contracts to deploy
 * @param owner the owner to set, if one was named
 * @param gate the guestbook's gate public key, if one was named
 * @param rpc the node to talk to
 * @param dryRun whether to stop before sending anything
 * @param commit the source commit being deployed
 */
public record Options(Network network, List<String> modules, Optional<Hash160> owner, Optional<ECPublicKey> gate,
        String rpc, boolean dryRun, String commit) {

    private static final List<String> ALL_MODULES = List.of("guestbook", "credits");

    /** Reads the command line: network, then contract, then flags. */
    public static Options parse(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Name a network and a contract.");
        }
        Network network = Network.named(args[0]);
        List<String> modules = modules(args[1]);
        Optional<Hash160> owner = Optional.empty();
        Optional<ECPublicKey> gate = Optional.empty();
        String rpc = network.defaultRpc();
        boolean dryRun = false;
        String commit = "unknown";

        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--owner" -> owner = Optional.of(Hash160.fromAddress(value(args, ++i)));
                case "--gate" -> gate = Optional.of(new ECPublicKey(value(args, ++i)));
                case "--rpc" -> rpc = value(args, ++i);
                case "--commit" -> commit = value(args, ++i);
                case "--dry-run" -> dryRun = true;
                default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        }
        return new Options(network, modules, owner, gate, rpc, dryRun, commit);
    }

    private static List<String> modules(String target) {
        if (target.equals("all")) return ALL_MODULES;
        if (ALL_MODULES.contains(target)) return List.of(target);
        throw new IllegalArgumentException("Unknown contract: " + target);
    }

    private static String value(String[] args, int index) {
        if (index >= args.length) throw new IllegalArgumentException(args[index - 1] + " needs a value.");
        return args[index];
    }
}
