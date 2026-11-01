package com.smartargs.mempolis.deploy;

import io.neow3j.types.Hash160;

import java.util.List;
import java.util.Optional;

/** The checks a mainnet deployment has to pass that a testnet one does not. */
public final class MainnetGuard {

    private MainnetGuard() {}

    /**
     * Fails unless the owner was named and is not the deploying key, and each contract is on testnet in exactly this
     * form.
     */
    public static void check(Optional<Hash160> owner, Hash160 deployer, List<ContractArtifact> artifacts,
            DeploymentLog testnet) {
        Hash160 namedOwner = owner.orElseThrow(() -> new IllegalStateException("Mainnet needs --owner."));
        if (namedOwner.equals(deployer)) {
            throw new IllegalStateException("The mainnet owner must not be the deploying key.");
        }
        artifacts.forEach(artifact -> requireOnTestnet(artifact, testnet));
    }

    private static void requireOnTestnet(ContractArtifact artifact, DeploymentLog testnet) {
        boolean matches = testnet.find(artifact.name())
                .filter(deployment -> deployment.fingerprint().equals(artifact.fingerprint()))
                .isPresent();
        if (!matches) throw new IllegalStateException("Deploy this build of " + artifact.name() + " to testnet first.");
    }
}
