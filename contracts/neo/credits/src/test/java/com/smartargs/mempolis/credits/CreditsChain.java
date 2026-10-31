package com.smartargs.mempolis.credits;

import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.NeoApplicationLog;
import io.neow3j.test.ContractTestExtension;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;
import io.neow3j.types.Hash256;
import io.neow3j.wallet.Account;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.smartargs.mempolis.testkit.TestHelper.assertHalted;
import static com.smartargs.mempolis.testkit.TestHelper.fund;
import static com.smartargs.mempolis.testkit.TestHelper.fundedAccount;
import static com.smartargs.mempolis.testkit.TestHelper.read;
import static com.smartargs.mempolis.testkit.TestHelper.send;
import static io.neow3j.types.ContractParameter.byteArray;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;

/** A running chain with the credits deployed, and the steps the tests share. */
final class CreditsChain {

    final Neow3j neow3j;
    final ContractTestExtension ext;
    final SmartContract credits;
    final Account owner;

    CreditsChain(ContractTestExtension ext, Account owner) throws Throwable {
        this.ext = ext;
        this.owner = owner;
        neow3j = ext.getNeow3j();
        neow3j.allowTransmissionOnFault();
        credits = ext.getDeployedContract(CityCredits.class);
        fund(neow3j, ext, owner.getScriptHash());
    }

    Account account() throws Throwable {
        return fundedAccount(neow3j, ext);
    }

    /** Posts a new root for these earned totals, replacing the last one. */
    MerkleTree publish(Map<Hash160, BigInteger> earned) throws Throwable {
        return publish(credits, earned);
    }

    MerkleTree publish(SmartContract contract, Map<Hash160, BigInteger> earned) throws Throwable {
        MerkleTree tree = new MerkleTree(earned);
        BigInteger next = read(contract, "getEpoch").getInteger().add(BigInteger.ONE);
        assertHalted(send(contract, "postRoot", owner, integer(next), byteArray(tree.root())), neow3j);
        return tree;
    }

    MerkleTree publish(Account wallet, long earned) throws Throwable {
        return publish(Map.of(wallet.getScriptHash(), BigInteger.valueOf(earned)));
    }

    Hash256 sendClaim(Account wallet, long earned, MerkleTree tree) throws Throwable {
        return sendClaim(credits, wallet, earned, tree);
    }

    Hash256 sendClaim(SmartContract contract, Account wallet, long earned, MerkleTree tree) throws Throwable {
        Hash160 hash = wallet.getScriptHash();
        return send(contract, "claim", wallet, claimParams(hash, earned, tree).toArray(new ContractParameter[0]));
    }

    List<ContractParameter> claimParams(Hash160 wallet, long earned, MerkleTree tree) {
        return List.of(hash160(wallet), integer(BigInteger.valueOf(earned)), integer(tree.index(wallet)),
                MerkleTree.proofParam(tree.proof(wallet)));
    }

    /** Publishes and claims in one step, for tests about what happens after a claim. */
    NeoApplicationLog.Execution claimFresh(Account wallet, long earned) throws Throwable {
        return assertHalted(sendClaim(wallet, earned, publish(wallet, earned)), neow3j);
    }

    /** A funded wallet that has claimed {@code credits} credits. */
    Account walletWith(long credits) throws Throwable {
        Account wallet = account();
        claimFresh(wallet, credits);
        return wallet;
    }

    BigInteger balanceOf(Account wallet) throws Throwable {
        return read(credits, "balanceOf", hash160(wallet)).getInteger();
    }

    static Map<Hash160, BigInteger> totals(Object... walletsAndAmounts) {
        Map<Hash160, BigInteger> totals = new LinkedHashMap<>();
        for (int i = 0; i < walletsAndAmounts.length; i += 2) {
            totals.put(((Account) walletsAndAmounts[i]).getScriptHash(),
                    BigInteger.valueOf((Long) walletsAndAmounts[i + 1]));
        }
        return totals;
    }
}
