package com.smartargs.mempolis.deploy;

import io.neow3j.contract.GasToken;
import io.neow3j.crypto.ECKeyPair.ECPublicKey;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.http.HttpService;
import io.neow3j.types.Hash160;
import io.neow3j.types.Hash256;
import io.neow3j.wallet.Account;

import java.io.Console;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The deploy command. Run it through {@code deploy.sh}, which builds the contracts first. */
public final class DeployCli {

    private static final String WIF_VARIABLE = "NEO_DEPLOYER_WIF";
    private static final String GATED_MODULE = "guestbook";
    private static final Path RECORDS = Path.of("deployments");
    private static final int GAS_DECIMALS = 8;

    private DeployCli() {}

    /** Runs the deployment that {@code args} describes and exits non-zero if it fails. */
    public static void main(String[] args) {
        try {
            run(Options.parse(args));
        } catch (Throwable failure) {
            System.err.println("Failed: " + failure.getMessage());
            System.exit(1);
        }
    }

    private static void run(Options options) throws Throwable {
        Neow3j neow3j = connect(options);
        Account sender = deployerAccount();
        Hash160 owner = options.owner().orElse(sender.getScriptHash());
        if (options.modules().contains(GATED_MODULE) && options.gate().isEmpty()) {
            throw new IllegalArgumentException("The guestbook needs --gate: the public key of its vouchers.");
        }
        Map<ContractArtifact, Optional<ECPublicKey>> gates = new LinkedHashMap<>();
        for (String module : options.modules()) {
            gates.put(ContractArtifact.loadFrom(Path.of(module)), module.equals(GATED_MODULE) ? options.gate() : Optional.empty());
        }
        List<ContractArtifact> artifacts = new ArrayList<>(gates.keySet());

        boolean mainnet = options.network() == Network.MAINNET;
        if (mainnet) {
            MainnetGuard.check(options.owner(), sender.getScriptHash(), artifacts, recordOf(Network.TESTNET));
        }

        Deployer deployer = new Deployer(neow3j, sender);
        List<DeploymentPlan> plans = new ArrayList<>();
        for (ContractArtifact artifact : artifacts) {
            plans.add(deployer.plan(artifact, owner, gates.get(artifact)));
        }

        BigInteger fee = plans.stream().map(plan -> BigInteger.valueOf(plan.fee())).reduce(BigInteger.ZERO, BigInteger::add);
        BigInteger balance = new GasToken(neow3j).getBalanceOf(sender);
        printPlan(options, sender, owner, plans, fee, balance);

        if (options.dryRun()) return;
        if (fee.signum() == 0 && recorded(options, plans)) return;
        if (balance.compareTo(fee) < 0) throw new IllegalStateException(sender.getAddress() + " has too little GAS.");
        if (mainnet && fee.signum() > 0) requireTypedConfirmation();

        DeploymentLog record = recordOf(options.network());
        for (DeploymentPlan plan : plans) {
            record.record(plan.artifact().name(), deliver(deployer, plan, sender, options.commit()));
        }
    }

    private static Neow3j connect(Options options) throws IOException {
        Neow3j neow3j = Neow3j.build(new HttpService(options.rpc()));
        long magic = neow3j.getNetworkMagic();
        if (magic != options.network().magic()) {
            throw new IllegalStateException(options.rpc() + " is not " + options.network().label() + ": magic " + magic);
        }
        return neow3j;
    }

    private static Account deployerAccount() {
        char[] wif = wif();
        try {
            return Account.fromWIF(new String(wif));
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException("The deployer key is not a valid WIF.");
        }
    }

    private static char[] wif() {
        String fromEnvironment = System.getenv(WIF_VARIABLE);
        if (fromEnvironment != null && !fromEnvironment.isBlank()) return fromEnvironment.trim().toCharArray();
        Console console = System.console();
        if (console == null) throw new IllegalStateException("Set " + WIF_VARIABLE + ": there is no terminal to ask on.");
        return console.readPassword("Deployer WIF: ");
    }

    private static DeploymentLog recordOf(Network network) throws IOException {
        return DeploymentLog.open(RECORDS.resolve(network.label() + ".json"));
    }

    private static boolean recorded(Options options, List<DeploymentPlan> plans) throws IOException {
        DeploymentLog record = recordOf(options.network());
        return plans.stream().allMatch(plan -> record.find(plan.artifact().name())
                .filter(deployment -> deployment.hash().equals("0x" + plan.hash()))
                .isPresent());
    }

    private static Deployment deliver(Deployer deployer, DeploymentPlan plan, Account sender, String commit)
            throws IOException {
        String transaction = null;
        if (!plan.alreadyDeployed()) {
            Hash256 sent = deployer.send(plan);
            System.out.println("Sent " + plan.artifact().name() + ": 0x" + sent);
            deployer.confirm(plan, sent);
            System.out.println("Confirmed 0x" + plan.hash());
            transaction = "0x" + sent;
        }
        ContractArtifact artifact = plan.artifact();
        return new Deployment("0x" + plan.hash(), transaction, plan.owner().toAddress(), sender.getAddress(),
                artifact.nef().getCheckSumAsInteger(), artifact.fingerprint(), commit);
    }

    private static void printPlan(Options options, Account sender, Hash160 owner, List<DeploymentPlan> plans,
            BigInteger fee, BigInteger balance) {
        System.out.println("Network   " + options.network().label() + " via " + options.rpc());
        System.out.println("Commit    " + options.commit());
        System.out.println("Deployer  " + sender.getAddress());
        System.out.println("Owner     " + owner.toAddress());
        for (DeploymentPlan plan : plans) {
            String state = plan.alreadyDeployed() ? "already deployed" : gas(BigInteger.valueOf(plan.fee())) + " GAS";
            System.out.println(plan.artifact().name() + ": 0x" + plan.hash() + " (" + state + ")");
        }
        System.out.println("Cost      " + gas(fee) + " GAS, balance " + gas(balance) + " GAS");
    }

    private static String gas(BigInteger fractions) {
        return new BigDecimal(fractions, GAS_DECIMALS).stripTrailingZeros().toPlainString();
    }

    private static void requireTypedConfirmation() {
        Console console = System.console();
        if (console == null) throw new IllegalStateException("Mainnet needs a terminal to confirm on.");
        System.out.println("This is permanent. Check the owner above: it can replace the contract's code.");
        String answer = console.readLine("Type mainnet to deploy: ");
        if (!Network.MAINNET.label().equals(answer)) throw new IllegalStateException("Not confirmed.");
    }
}
