package com.smartargs.mempolis.credits;

import com.smartargs.mempolis.testkit.ReadyNeoExpressContainer;
import com.smartargs.mempolis.testkit.TestHelper;
import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.core.response.NeoApplicationLog;
import io.neow3j.protocol.core.response.Notification;
import io.neow3j.test.ContractTest;
import io.neow3j.test.ContractTestExtension;
import io.neow3j.test.DeployConfig;
import io.neow3j.test.DeployConfiguration;
import io.neow3j.test.DeployContext;
import io.neow3j.transaction.AccountSigner;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;
import io.neow3j.types.Hash256;
import io.neow3j.wallet.Account;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.smartargs.mempolis.credits.CreditsChain.totals;
import static com.smartargs.mempolis.testkit.TestHelper.assertAborted;
import static com.smartargs.mempolis.testkit.TestHelper.assertHalted;
import static com.smartargs.mempolis.testkit.TestHelper.deploy;
import static com.smartargs.mempolis.testkit.TestHelper.send;
import static com.smartargs.mempolis.testkit.TestHelper.sendTwice;
import static io.neow3j.types.ContractParameter.any;
import static io.neow3j.types.ContractParameter.byteArray;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;
import static org.assertj.core.api.Assertions.assertThat;

@ContractTest(blockTime = 1, contracts = CityCredits.class, configFile = TestHelper.NODE_CONFIG)
public class CityCreditsClaimTest {

    @RegisterExtension
    private static final ContractTestExtension ext = new ContractTestExtension(new ReadyNeoExpressContainer());

    private static final Account owner = Account.create();

    private static CreditsChain chain;
    private static SmartContract credits;

    @DeployConfig(CityCredits.class)
    public static DeployConfiguration configure(DeployContext ctx) {
        DeployConfiguration config = new DeployConfiguration();
        config.setDeployParam(hash160(owner.getScriptHash()));
        return config;
    }

    @BeforeAll
    static void setUp() throws Throwable {
        chain = new CreditsChain(ext, owner);
        credits = chain.credits;
    }

    @Test
    void claim_creditsTheWalletAndFiresClaimed() throws Throwable {
        Account wallet = chain.account();

        NeoApplicationLog.Execution execution = chain.claimFresh(wallet, 100);

        assertThat(execution.getStack().get(0).getInteger()).isEqualTo(BigInteger.valueOf(100));
        assertThat(chain.balanceOf(wallet)).isEqualTo(BigInteger.valueOf(100));
        Notification claimed = execution.getNotifications().get(0);
        assertThat(claimed.getEventName()).isEqualTo("Claimed");
        assertThat(claimed.getState().getList().get(0).getByteArray())
                .isEqualTo(wallet.getScriptHash().toLittleEndianArray());
        assertThat(claimed.getState().getList().get(1).getInteger()).isEqualTo(BigInteger.valueOf(100));
        assertThat(claimed.getState().getList().get(2).getInteger()).isEqualTo(BigInteger.valueOf(100));
    }

    @Test
    void claim_worksForEveryLeafOfAPaddedTree() throws Throwable {
        Account[] wallets = {chain.account(), chain.account(), chain.account(), chain.account(), chain.account()};
        long[] amounts = {1, 127, 128, 255, 1_000_000_000_000L};
        MerkleTree tree = chain.publish(totals(wallets[0], amounts[0], wallets[1], amounts[1], wallets[2], amounts[2],
                wallets[3], amounts[3], wallets[4], amounts[4]));

        for (int i = 0; i < wallets.length; i++) {
            assertHalted(chain.sendClaim(wallets[i], amounts[i], tree), chain.neow3j);
            assertThat(chain.balanceOf(wallets[i])).isEqualTo(BigInteger.valueOf(amounts[i]));
        }
    }

    @Test
    void claim_onlyCreditsWhatWasEarnedSinceTheLastClaim() throws Throwable {
        Account wallet = chain.account();
        chain.claimFresh(wallet, 100);

        NeoApplicationLog.Execution second = chain.claimFresh(wallet, 130);

        assertThat(second.getStack().get(0).getInteger()).isEqualTo(BigInteger.valueOf(30));
        assertThat(chain.balanceOf(wallet)).isEqualTo(BigInteger.valueOf(130));
    }

    @Test
    void claim_theSameProofTwice_aborts() throws Throwable {
        Account wallet = chain.account();
        MerkleTree tree = chain.publish(wallet, 100);
        assertHalted(chain.sendClaim(wallet, 100, tree), chain.neow3j);

        assertAborted(chain.sendClaim(wallet, 100, tree), "CC: nothing to claim", chain.neow3j);

        assertThat(chain.balanceOf(wallet)).isEqualTo(BigInteger.valueOf(100));
    }

    @Test
    void claim_aLowerTotalThanAlreadyClaimed_aborts() throws Throwable {
        Account wallet = chain.account();
        chain.claimFresh(wallet, 100);

        assertAborted(chain.sendClaim(wallet, 40, chain.publish(wallet, 40)), "CC: nothing to claim", chain.neow3j);
    }

    @Test
    void claim_anInflatedTotal_aborts() throws Throwable {
        Account wallet = chain.account();

        assertAborted(chain.sendClaim(wallet, 101, chain.publish(wallet, 100)), "CC: bad proof", chain.neow3j);
    }

    @Test
    void claim_withAnotherWalletsProof_aborts() throws Throwable {
        Account earner = chain.account();
        Account thief = chain.account();
        MerkleTree tree = chain.publish(earner, 100);
        List<ContractParameter> stolen = new ArrayList<>(chain.claimParams(earner.getScriptHash(), 100, tree));
        stolen.set(0, hash160(thief));

        assertAborted(send(credits, "claim", thief, stolen.toArray(new ContractParameter[0])), "CC: bad proof",
                chain.neow3j);
    }

    @Test
    void claim_forAnotherWallet_aborts() throws Throwable {
        Account earner = chain.account();
        Account stranger = chain.account();
        MerkleTree tree = chain.publish(earner, 100);
        List<ContractParameter> params = chain.claimParams(earner.getScriptHash(), 100, tree);

        assertAborted(send(credits, "claim", stranger, params.toArray(new ContractParameter[0])), "CC: no witness",
                chain.neow3j);
    }

    @Test
    void claim_againstAReplacedRoot_aborts() throws Throwable {
        Account wallet = chain.account();
        MerkleTree stale = chain.publish(wallet, 100);
        chain.publish(wallet, 120);

        assertAborted(chain.sendClaim(wallet, 100, stale), "CC: bad proof", chain.neow3j);
    }

    @Test
    void claim_beforeAnyRoot_aborts() throws Throwable {
        Account wallet = chain.account();
        SmartContract fresh = deploy(CityCredits.class, chain.account(), hash160(owner), chain.neow3j);
        MerkleTree tree = new MerkleTree(totals(wallet, 100L));

        assertAborted(chain.sendClaim(fresh, wallet, 100, tree), "CC: bad proof", chain.neow3j);
    }

    @Test
    void claim_withATruncatedProof_aborts() throws Throwable {
        Account first = chain.account();
        Account second = chain.account();
        MerkleTree tree = chain.publish(totals(first, 10L, second, 20L));
        ContractParameter proof = MerkleTree.proofParam(List.of());

        assertAborted(claimWith(first, 10, tree.index(first.getScriptHash()), proof), "CC: bad proof", chain.neow3j);
    }

    @Test
    void claim_withTheWrongPosition_aborts() throws Throwable {
        Account first = chain.account();
        Account second = chain.account();
        MerkleTree tree = chain.publish(totals(first, 10L, second, 20L));
        ContractParameter proof = MerkleTree.proofParam(tree.proof(first.getScriptHash()));

        assertAborted(claimWith(first, 10, 1, proof), "CC: bad proof", chain.neow3j);
    }

    @Test
    void claim_withANegativePosition_aborts() throws Throwable {
        Account wallet = chain.account();
        MerkleTree tree = chain.publish(wallet, 100);
        ContractParameter proof = MerkleTree.proofParam(tree.proof(wallet.getScriptHash()));

        assertAborted(claimWith(wallet, 100, -1, proof), "CC: bad proof", chain.neow3j);
    }

    @Test
    void claim_withAnOverlongProof_aborts() throws Throwable {
        Account wallet = chain.account();
        chain.publish(wallet, 100);
        ContractParameter proof = MerkleTree.proofParam(Collections.nCopies(33, new byte[32]));

        assertAborted(claimWith(wallet, 100, 0, proof), "CC: bad proof", chain.neow3j);
    }

    @Test
    void claim_withAMisSizedProofEntry_aborts() throws Throwable {
        Account wallet = chain.account();
        chain.publish(wallet, 100);
        ContractParameter proof = MerkleTree.proofParam(List.of(new byte[31]));

        assertAborted(claimWith(wallet, 100, 0, proof), "CC: bad proof", chain.neow3j);
    }

    @Test
    void claim_withTheZeroHash_aborts() throws Throwable {
        Account stranger = chain.account();

        assertAborted(send(credits, "claim", stranger, hash160(Hash160.ZERO), integer(1), integer(0),
                MerkleTree.proofParam(List.of())), "CC: bad wallet", chain.neow3j);
    }

    @Test
    void claim_withAMalformedWallet_aborts() throws Throwable {
        Account stranger = chain.account();

        assertAborted(send(credits, "claim", stranger, byteArray(new byte[33]), integer(1), integer(0),
                MerkleTree.proofParam(List.of())), "CC: bad wallet", chain.neow3j);
    }

    @Test
    void claim_withoutAWallet_aborts() throws Throwable {
        Account stranger = chain.account();

        assertAborted(send(credits, "claim", stranger, any(null), integer(1), integer(0),
                MerkleTree.proofParam(List.of())), "CC: bad wallet", chain.neow3j);
    }

    @Test
    void claim_twiceInOneTransaction_aborts() throws Throwable {
        Account wallet = chain.account();
        MerkleTree tree = chain.publish(wallet, 100);
        List<ContractParameter> params = chain.claimParams(wallet.getScriptHash(), 100, tree);

        assertAborted(sendTwice(chain.neow3j, credits, "claim", params, params, AccountSigner.calledByEntry(wallet)), "CC: re-entry",
                chain.neow3j);

        assertThat(chain.balanceOf(wallet)).isZero();
    }

    private static Hash256 claimWith(Account wallet, long earned, int index, ContractParameter proof) throws Throwable {
        return send(credits, "claim", wallet, hash160(wallet), integer(BigInteger.valueOf(earned)), integer(index),
                proof);
    }
}
