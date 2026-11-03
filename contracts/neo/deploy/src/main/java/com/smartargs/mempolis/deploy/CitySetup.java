package com.smartargs.mempolis.deploy;

import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.NeoApplicationLog;
import io.neow3j.transaction.Transaction;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;
import io.neow3j.types.Hash256;
import io.neow3j.types.NeoVMStateType;
import io.neow3j.utils.Await;
import io.neow3j.wallet.Account;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.byteArray;
import static io.neow3j.types.ContractParameter.integer;

/**
 * Brings a deed contract's lot registry and free queue up to a {@link CityPlanFile}, signed by the contract's owner.
 * It appends only what the chain does not hold yet, so an interrupted run can simply be repeated, and it refuses a
 * plan that disagrees with what is already registered.
 */
public final class CitySetup {

    /** Lots or queue places sent in one transaction, the contract's batch limit. */
    public static final int BATCH = 200;

    private final Neow3j neow3j;
    private final SmartContract deed;
    private final SignedTransactions transactions;

    public CitySetup(Neow3j neow3j, Hash160 deed, Account owner) {
        this.neow3j = neow3j;
        this.deed = new SmartContract(deed, neow3j);
        this.transactions = new SignedTransactions(neow3j, owner);
    }

    /** How many lots and queue places the chain still lacks, after checking that what it holds matches {@code plan}. */
    public int[] missing(CityPlanFile plan) throws IOException {
        int lots = readInt("lotCount"), queue = readInt("queueLength");
        if (lots > plan.lots().size() || queue > plan.queue().size()) {
            throw new IllegalStateException("The chain holds more than the plan: " + lots + " lots, " + queue + " queued.");
        }
        verify(plan, lots, queue);
        return new int[]{plan.lots().size() - lots, plan.queue().size() - queue};
    }

    /** Sends what the chain lacks, one batch at a time, then checks everything against {@code plan}. */
    public void apply(CityPlanFile plan) throws IOException {
        for (int first = readInt("lotCount") + 1; first <= plan.lots().size(); first += BATCH) {
            List<Integer> batch = plan.lots().subList(first - 1, Math.min(first - 1 + BATCH, plan.lots().size()));
            byte[] infos = new byte[batch.size()];
            for (int i = 0; i < infos.length; i++) infos[i] = batch.get(i).byteValue();
            send("registerLots", integer(first), byteArray(infos));
            System.out.println("Registered lots " + first + " to " + (first + infos.length - 1));
        }
        for (int first = readInt("queueLength") + 1; first <= plan.queue().size(); first += BATCH) {
            List<Integer> batch = plan.queue().subList(first - 1, Math.min(first - 1 + BATCH, plan.queue().size()));
            send("queueLots", integer(first), array(batch));
            System.out.println("Queued places " + first + " to " + (first + batch.size() - 1));
        }
        verify(plan, plan.lots().size(), plan.queue().size());
    }

    private void verify(CityPlanFile plan, int lots, int queue) throws IOException {
        for (int lot = 1; lot <= lots; lot++) {
            int info = plan.lots().get(lot - 1);
            if (readInt("tierOf", integer(lot)) != (info & 7) || readInt("ringOf", integer(lot)) != info / 8) {
                throw new IllegalStateException("Lot " + lot + " on chain differs from the plan.");
            }
        }
        for (int place = 1; place <= queue; place++) {
            if (readInt("queueAt", integer(place)) != plan.queue().get(place - 1)) {
                throw new IllegalStateException("Queue place " + place + " on chain differs from the plan.");
            }
        }
    }

    private void send(String method, ContractParameter... params) throws IOException {
        byte[] script = new io.neow3j.script.ScriptBuilder().contractCall(deed.getScriptHash(), method, List.of(params)).toArray();
        Transaction transaction = transactions.create(script);
        Hash256 hash = transaction.send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(hash, neow3j);
        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(hash).send().getApplicationLog().getExecutions().get(0);
        if (execution.getState() != NeoVMStateType.HALT) throw new IllegalStateException(method + " faulted: " + execution.getException());
    }

    private int readInt(String method, ContractParameter... params) throws IOException {
        BigInteger value = deed.callInvokeFunction(method, List.of(params)).getInvocationResult().getStack().get(0).getInteger();
        return value.intValueExact();
    }
}
