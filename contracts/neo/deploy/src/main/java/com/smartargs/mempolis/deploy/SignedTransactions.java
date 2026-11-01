package com.smartargs.mempolis.deploy;

import io.neow3j.constants.NeoConstants;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.InvocationResult;
import io.neow3j.transaction.AccountSigner;
import io.neow3j.transaction.Signer;
import io.neow3j.transaction.Transaction;
import io.neow3j.utils.Numeric;
import io.neow3j.wallet.Account;

import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds signed transactions for one account. {@code TransactionBuilder} prices a transaction with a witness for a
 * placeholder key, which neo-go nodes reject as not belonging to the signer, so this prices it with the account's own.
 */
public final class SignedTransactions {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long NONCE_MASK = 0xFFFFFFFFL;

    private final Neow3j neow3j;
    private final Account sender;

    public SignedTransactions(Neow3j neow3j, Account sender) {
        this.neow3j = neow3j;
        this.sender = sender;
    }

    /** Simulates {@code script}, fails if it faults, and returns it as a priced and signed transaction. */
    public Transaction create(byte[] script) throws IOException {
        List<Signer> signers = List.of(AccountSigner.calledByEntry(sender));
        long systemFee = simulate(script, signers);
        long nonce = RANDOM.nextInt() & NONCE_MASK;
        long validUntilBlock = neow3j.getBlockCount().send().getBlockCount().longValue()
                + neow3j.getMaxValidUntilBlockIncrement() - 1;

        Transaction unpriced = signed(script, signers, nonce, validUntilBlock, systemFee, 0);
        long networkFee = neow3j.calculateNetworkFee(Numeric.toHexStringNoPrefix(unpriced.toArray())).send()
                .getNetworkFee().getNetworkFee().longValue();
        return signed(script, signers, nonce, validUntilBlock, systemFee, networkFee);
    }

    private long simulate(byte[] script, List<Signer> signers) throws IOException {
        InvocationResult result = neow3j.invokeScript(Numeric.toHexStringNoPrefix(script),
                signers.toArray(new Signer[0])).send().getInvocationResult();
        if (result.hasStateFault()) throw new IllegalStateException("It would fault: " + result.getException());
        return new BigInteger(result.getGasConsumed()).longValueExact();
    }

    private Transaction signed(byte[] script, List<Signer> signers, long nonce, long validUntilBlock, long systemFee,
            long networkFee) throws IOException {
        return new Transaction(neow3j, NeoConstants.CURRENT_TX_VERSION, nonce, validUntilBlock, signers, systemFee,
                networkFee, new ArrayList<>(), script, new ArrayList<>()).addWitness(sender);
    }
}
