package com.smartargs.mempolis.deploy;

import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.http.HttpService;
import io.neow3j.types.Hash160;
import io.neow3j.wallet.Account;

import java.io.Console;
import java.nio.file.Path;

/**
 * The setup command: loads a city's lot registry and free queue onto its deployed deed contract. Run it through
 * {@code deploy.sh setup}. Usage: {@code setup <network> --plan <file> [--deed <hash>] [--rpc <url>] [--dry-run]}.
 */
public final class SetupCli {

    private static final String WIF_VARIABLE = "NEO_OWNER_WIF";
    private static final String DEED_CONTRACT = "MempolisDeed";
    private static final Path RECORDS = Path.of("deployments");

    private SetupCli() {}

    /** Runs the setup that {@code args} describes and exits non-zero if it fails. */
    public static void main(String[] args) {
        try {
            run(args);
        } catch (Throwable failure) {
            System.err.println("Failed: " + failure.getMessage());
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Throwable {
        if (args.length < 1) throw new IllegalArgumentException("Name a network.");
        Network network = Network.named(args[0]);
        Path planFile = null;
        Hash160 deed = null;
        String rpc = network.defaultRpc();
        boolean dryRun = false;
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--plan" -> planFile = Path.of(value(args, ++i));
                case "--deed" -> deed = new Hash160(value(args, ++i));
                case "--rpc" -> rpc = value(args, ++i);
                case "--dry-run" -> dryRun = true;
                default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        }
        if (planFile == null) throw new IllegalArgumentException("Name the plan file with --plan.");
        if (deed == null) deed = recordedDeed(network);

        Neow3j neow3j = Neow3j.build(new HttpService(rpc));
        if (neow3j.getNetworkMagic() != network.magic()) throw new IllegalStateException(rpc + " is not " + network.label());
        CityPlanFile plan = CityPlanFile.read(planFile);
        Account owner = ownerAccount();
        CitySetup setup = new CitySetup(neow3j, deed, owner);

        int[] missing = setup.missing(plan);
        System.out.println("Deed      0x" + deed + " on " + network.label());
        System.out.println("Owner     " + owner.getAddress());
        System.out.println("Plan      " + plan.lots().size() + " lots, " + plan.queue().size() + " queued");
        System.out.println("Missing   " + missing[0] + " lots, " + missing[1] + " queue places");
        if (dryRun || (missing[0] == 0 && missing[1] == 0)) return;
        if (network == Network.MAINNET) requireTypedConfirmation();
        setup.apply(plan);
        System.out.println("Done: the chain matches the plan.");
    }

    private static Hash160 recordedDeed(Network network) throws Exception {
        return DeploymentLog.open(RECORDS.resolve(network.label() + ".json")).find(DEED_CONTRACT)
                .map(deployment -> new Hash160(deployment.hash()))
                .orElseThrow(() -> new IllegalArgumentException("No deed contract recorded on " + network.label() + "; pass --deed."));
    }

    private static Account ownerAccount() {
        String fromEnvironment = System.getenv(WIF_VARIABLE);
        if (fromEnvironment != null && !fromEnvironment.isBlank()) return Account.fromWIF(fromEnvironment.trim());
        Console console = System.console();
        if (console == null) throw new IllegalStateException("Set " + WIF_VARIABLE + ": there is no terminal to ask on.");
        return Account.fromWIF(new String(console.readPassword("Owner WIF: ")));
    }

    private static void requireTypedConfirmation() {
        Console console = System.console();
        if (console == null) throw new IllegalStateException("Mainnet needs a terminal to confirm on.");
        System.out.println("Registered lots and queue places can never be changed.");
        String answer = console.readLine("Type mainnet to register them: ");
        if (!Network.MAINNET.label().equals(answer)) throw new IllegalStateException("Not confirmed.");
    }

    private static String value(String[] args, int index) {
        if (index >= args.length) throw new IllegalArgumentException(args[index - 1] + " needs a value.");
        return args[index];
    }
}
