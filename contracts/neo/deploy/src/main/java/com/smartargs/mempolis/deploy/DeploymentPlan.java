package com.smartargs.mempolis.deploy;

import io.neow3j.crypto.ECKeyPair.ECPublicKey;
import io.neow3j.transaction.Transaction;
import io.neow3j.types.Hash160;

import java.util.Optional;

/**
 * What deploying one contract would do.
 *
 * @param artifact the contract
 * @param owner the owner it will have
 * @param gate the public key of its vouchers, for a contract that has them
 * @param hash the contract's address
 * @param transaction the signed deployment, absent when the contract is already on chain
 */
public record DeploymentPlan(ContractArtifact artifact, Hash160 owner, Optional<ECPublicKey> gate, Hash160 hash,
        Optional<Transaction> transaction) {

    /** Whether the contract is already on chain, so nothing needs sending. */
    public boolean alreadyDeployed() {
        return transaction.isEmpty();
    }

    /** The GAS this deployment costs, in fractions, 0 when nothing needs sending. */
    public long fee() {
        return transaction.map(tx -> tx.getSystemFee() + tx.getNetworkFee()).orElse(0L);
    }
}
