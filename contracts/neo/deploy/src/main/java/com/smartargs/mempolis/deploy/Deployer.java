package com.smartargs.mempolis.deploy;

import io.neow3j.contract.ContractManagement;
import io.neow3j.contract.SmartContract;
import io.neow3j.crypto.ECKeyPair.ECPublicKey;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.ContractState;
import io.neow3j.protocol.core.response.NeoApplicationLog;
import io.neow3j.protocol.core.response.NeoSendRawTransaction;
import io.neow3j.transaction.Transaction;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;
import io.neow3j.types.Hash256;
import io.neow3j.types.NeoVMStateType;
import io.neow3j.utils.Await;
import io.neow3j.wallet.Account;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.publicKey;

/** Plans and sends deployments from one account and checks what ended up on chain. */
public final class Deployer {

    private final Neow3j neow3j;
    private final Account sender;
    private final SignedTransactions transactions;

    public Deployer(Neow3j neow3j, Account sender) {
        this.neow3j = neow3j;
        this.sender = sender;
        this.transactions = new SignedTransactions(neow3j, sender);
    }

    /**
     * Works out what deploying {@code artifact} would do, without sending anything. The node simulates the deployment,
     * so one that would fault is refused here. A contract already on chain is checked against what was asked for. A
     * contract gated by vouchers is given the {@code gate} public key along with its owner.
     */
    public DeploymentPlan plan(ContractArtifact artifact, Hash160 owner, Optional<ECPublicKey> gate) throws Throwable {
        Hash160 hash = SmartContract.calcContractHash(sender.getScriptHash(), artifact.nef().getCheckSumAsInteger(),
                artifact.name());
        if (isOnChain(hash)) {
            verify(artifact, owner, gate, hash);
            return new DeploymentPlan(artifact, owner, gate, hash, Optional.empty());
        }
        byte[] script = new ContractManagement(neow3j)
                .deploy(artifact.nef(), artifact.manifest(), deployData(owner, gate))
                .getScript();
        return new DeploymentPlan(artifact, owner, gate, hash, Optional.of(transactions.create(script)));
    }

    /** Sends the plan's signed transaction without waiting for it. */
    public Hash256 send(DeploymentPlan plan) throws IOException {
        Transaction transaction = plan.transaction()
                .orElseThrow(() -> new IllegalStateException(plan.artifact().name() + " is already deployed."));
        NeoSendRawTransaction response = transaction.send();
        response.throwOnError();
        return response.getSendRawTransaction().getHash();
    }

    /** Waits for {@code transaction} and fails unless the contract is on chain as the plan said. */
    public void confirm(DeploymentPlan plan, Hash256 transaction) throws IOException {
        Await.waitUntilTransactionIsExecuted(transaction, neow3j);
        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(transaction).send().getApplicationLog()
                .getExecutions().get(0);
        if (execution.getState() != NeoVMStateType.HALT) {
            throw new IllegalStateException(plan.artifact().name() + " faulted: " + execution.getException());
        }
        verify(plan.artifact(), plan.owner(), plan.gate(), plan.hash());
    }

    private static ContractParameter deployData(Hash160 owner, Optional<ECPublicKey> gate) {
        return gate.map(key -> array(hash160(owner), publicKey(key.getEncoded(true)))).orElse(hash160(owner));
    }

    private boolean isOnChain(Hash160 hash) throws IOException {
        return !neow3j.getContractState(hash).send().hasError();
    }

    private byte[] gateOf(Hash160 hash) throws IOException {
        return new SmartContract(hash, neow3j).callInvokeFunction("getGate").getInvocationResult().getStack().get(0)
                .getByteArray();
    }

    private void verify(ContractArtifact artifact, Hash160 owner, Optional<ECPublicKey> gate, Hash160 hash)
            throws IOException {
        ContractState state = neow3j.getContractState(hash).send().getContractState();
        if (state.getNef().getChecksum() != artifact.nef().getCheckSumAsInteger()) {
            throw new IllegalStateException(artifact.name() + " at 0x" + hash + " has different code.");
        }
        if (!state.getManifest().equals(artifact.manifest())) {
            throw new IllegalStateException(artifact.name() + " at 0x" + hash + " has a different manifest.");
        }
        Hash160 actualOwner = new SmartContract(hash, neow3j).callFunctionReturningScriptHash("getOwner");
        if (!actualOwner.equals(owner)) {
            throw new IllegalStateException(artifact.name() + " at 0x" + hash + " is owned by "
                    + actualOwner.toAddress() + ", not " + owner.toAddress() + ".");
        }
        if (gate.isPresent() && !Arrays.equals(gateOf(hash), gate.get().getEncoded(true))) {
            throw new IllegalStateException(artifact.name() + " at 0x" + hash + " has a different gate.");
        }
    }
}
