package com.smartargs.mempolis.deploy;

/**
 * One contract as it was deployed.
 *
 * @param hash the contract's script hash, as front-ends and explorers write it
 * @param transaction the deploying transaction, null when the contract was found on chain and not sent by this run
 * @param owner the owner's address
 * @param deployer the address that paid for the deployment
 * @param nefChecksum the NEF checksum {@code getcontractstate} reports
 * @param fingerprint see {@link ContractArtifact#fingerprint()}
 * @param commit the source commit, ending in {@code -dirty} if the tree had changes
 */
public record Deployment(String hash, String transaction, String owner, String deployer, long nefChecksum,
        String fingerprint, String commit) {
}
