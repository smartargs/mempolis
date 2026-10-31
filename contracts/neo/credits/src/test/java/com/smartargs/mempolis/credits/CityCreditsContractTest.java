package com.smartargs.mempolis.credits;

import com.smartargs.mempolis.credits.testsupport.UpdatedCredits;
import com.smartargs.mempolis.testkit.ReadyNeoExpressContainer;
import com.smartargs.mempolis.testkit.TestHelper;
import io.neow3j.contract.GasToken;
import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.core.response.ContractManifest;
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
import io.neow3j.types.StackItemType;
import io.neow3j.wallet.Account;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

import static com.smartargs.mempolis.testkit.TestHelper.assertAborted;
import static com.smartargs.mempolis.testkit.TestHelper.assertFaulted;
import static com.smartargs.mempolis.testkit.TestHelper.assertHalted;
import static com.smartargs.mempolis.testkit.TestHelper.assertReadAborts;
import static com.smartargs.mempolis.testkit.TestHelper.deploy;
import static com.smartargs.mempolis.testkit.TestHelper.read;
import static com.smartargs.mempolis.testkit.TestHelper.send;
import static com.smartargs.mempolis.testkit.TestHelper.sendDeploy;
import static com.smartargs.mempolis.testkit.TestHelper.sendTwice;
import static com.smartargs.mempolis.testkit.TestHelper.sendUpdate;
import static com.smartargs.mempolis.testkit.TestHelper.updateParams;
import static io.neow3j.types.ContractParameter.any;
import static io.neow3j.types.ContractParameter.byteArray;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;
import static org.assertj.core.api.Assertions.assertThat;

@ContractTest(blockTime = 1, contracts = CityCredits.class, configFile = TestHelper.NODE_CONFIG)
public class CityCreditsContractTest {

    @RegisterExtension
    private static final ContractTestExtension ext = new ContractTestExtension(new ReadyNeoExpressContainer());

    private static final Account owner = Account.create();

    private static CreditsChain chain;
    private static Account stranger;

    @DeployConfig(CityCredits.class)
    public static DeployConfiguration configure(DeployContext ctx) {
        DeployConfiguration config = new DeployConfiguration();
        config.setDeployParam(hash160(owner.getScriptHash()));
        return config;
    }

    @BeforeAll
    static void setUp() throws Throwable {
        chain = new CreditsChain(ext, owner);
        stranger = chain.account();
    }

    @Test
    void deploy_storesTheOwnerAndStartsWithoutARoot() throws Throwable {
        SmartContract fresh = deploy(CityCredits.class, chain.account(), hash160(owner), chain.neow3j);

        assertThat(read(fresh, "getOwner").getByteArray()).isEqualTo(owner.getScriptHash().toLittleEndianArray());
        assertThat(read(fresh, "getEpoch").getInteger()).isZero();
        assertThat(read(fresh, "getRoot").getType()).isEqualTo(StackItemType.ANY);
    }

    @Test
    void deploy_withoutAnOwner_aborts() throws Throwable {
        assertDeployAborts(any(null));
    }

    @Test
    void deploy_withAZeroOwner_aborts() throws Throwable {
        assertDeployAborts(hash160(Hash160.ZERO));
    }

    @Test
    void deploy_withAMalformedOwner_aborts() throws Throwable {
        assertDeployAborts(byteArray(new byte[33]));
    }

    @Test
    void deploy_withANonHashOwner_aborts() throws Throwable {
        assertDeployAborts(integer(1));
    }

    @Test
    void postRoot_advancesTheEpochAndStoresTheRoot() throws Throwable {
        byte[] root = new MerkleTree(CreditsChain.totals(stranger, 10L)).root();
        BigInteger next = read(chain.credits, "getEpoch").getInteger().add(BigInteger.ONE);

        Hash256 tx = send(chain.credits, "postRoot", owner, integer(next), byteArray(root));

        Notification posted = assertHalted(tx, chain.neow3j).getNotifications().get(0);
        assertThat(posted.getEventName()).isEqualTo("RootPosted");
        assertThat(posted.getState().getList().get(0).getInteger()).isEqualTo(next);
        assertThat(posted.getState().getList().get(1).getByteArray()).isEqualTo(root);
        assertThat(read(chain.credits, "getEpoch").getInteger()).isEqualTo(next);
        assertThat(read(chain.credits, "getRoot").getByteArray()).isEqualTo(root);
    }

    @Test
    void postRoot_byAStranger_aborts() throws Throwable {
        assertAborted(send(chain.credits, "postRoot", stranger, nextEpoch(), byteArray(new byte[32])), "CC: not owner",
                chain.neow3j);
    }

    @Test
    void postRoot_skippingAnEpoch_aborts() throws Throwable {
        BigInteger skipped = read(chain.credits, "getEpoch").getInteger().add(BigInteger.TWO);

        assertAborted(send(chain.credits, "postRoot", owner, integer(skipped), anyRoot()), "CC: bad epoch",
                chain.neow3j);
    }

    @Test
    void postRoot_repeatingAnEpoch_aborts() throws Throwable {
        ContractParameter current = integer(read(chain.credits, "getEpoch").getInteger());

        assertAborted(send(chain.credits, "postRoot", owner, current, anyRoot()), "CC: bad epoch", chain.neow3j);
    }

    @Test
    void postRoot_withAZeroRoot_aborts() throws Throwable {
        assertAborted(send(chain.credits, "postRoot", owner, nextEpoch(), byteArray(new byte[32])), "CC: bad root",
                chain.neow3j);
    }

    @Test
    void postRoot_withAShortRoot_aborts() throws Throwable {
        assertAborted(send(chain.credits, "postRoot", owner, nextEpoch(), byteArray(new byte[31])), "CC: bad root",
                chain.neow3j);
    }

    @Test
    void postRoot_withoutARoot_aborts() throws Throwable {
        assertAborted(send(chain.credits, "postRoot", owner, nextEpoch(), any(null)), "CC: bad root", chain.neow3j);
    }

    @Test
    void postRoot_twiceInOneTransaction_aborts() throws Throwable {
        ContractParameter epoch = nextEpoch();

        assertAborted(sendTwice(chain.neow3j, chain.credits, "postRoot", List.of(epoch, anyRoot()), List.of(epoch, anyRoot()),
                AccountSigner.calledByEntry(owner)), "CC: re-entry", chain.neow3j);
    }

    @Test
    void balanceOf_aWalletWithNoHistory_isZero() throws Throwable {
        assertThat(read(chain.credits, "balanceOf", hash160(Account.create())).getInteger()).isZero();
    }

    @Test
    void balanceOf_aMalformedWallet_aborts() throws Throwable {
        assertReadAborts(chain.credits, "balanceOf", "CC: bad wallet", byteArray(new byte[33]));
    }

    @Test
    void manifest_offersNoTransferAndClaimsNoTokenStandard() throws Throwable {
        ContractManifest manifest = chain.credits.getManifest();

        assertThat(manifest.getSupportedStandards()).isEmpty();
        assertThat(manifest.getAbi().getMethods().stream()
                .map(ContractManifest.ContractABI.ContractMethod::getName).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("_deploy", "postRoot", "claim", "update", "getOwner", "getEpoch",
                        "getRoot", "balanceOf");
    }

    @Test
    void manifest_canOnlyCallUpdateAndSha256() throws Throwable {
        assertThat(chain.credits.getManifest().getPermissions().stream()
                .flatMap(permission -> permission.getMethods().stream()).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("update", "sha256");
    }

    @Test
    void sendingGas_faultsSoNoFundsCanEverStrand() throws Throwable {
        Hash256 tx = new GasToken(chain.neow3j).transfer(stranger, chain.credits.getScriptHash(), BigInteger.ONE)
                .sign().send().getSendRawTransaction().getHash();

        assertFaulted(tx, chain.neow3j);
    }

    @Test
    void update_byAStranger_aborts() throws Throwable {
        assertAborted(sendUpdate(chain.credits, UpdatedCredits.class, stranger), "CC: not owner", chain.neow3j);
    }

    @Test
    void update_twiceInOneTransaction_aborts() throws Throwable {
        List<ContractParameter> update = updateParams(CityCredits.class, any(null));

        assertAborted(sendTwice(chain.neow3j, chain.credits, "update", update, update, AccountSigner.calledByEntry(owner)),
                "CC: re-entry", chain.neow3j);
    }

    @Test
    void update_byTheOwner_swapsTheCodeAndKeepsBalances() throws Throwable {
        Account wallet = chain.account();
        SmartContract instance = deploy(CityCredits.class, chain.account(), hash160(owner), chain.neow3j);
        MerkleTree tree = chain.publish(instance, CreditsChain.totals(wallet, 75L));
        assertHalted(chain.sendClaim(instance, wallet, 75, tree), chain.neow3j);

        assertHalted(sendUpdate(instance, UpdatedCredits.class, owner), chain.neow3j);

        assertThat(read(instance, "version").getInteger()).isEqualTo(BigInteger.TWO);
        assertThat(read(instance, "balanceOf", hash160(wallet)).getInteger()).isEqualTo(BigInteger.valueOf(75));
    }

    @Test
    void update_neverChangesTheOwnerWhateverDataItCarries() throws Throwable {
        SmartContract instance = deploy(CityCredits.class, chain.account(), hash160(owner), chain.neow3j);

        assertHalted(sendUpdate(instance, CityCredits.class, owner, hash160(stranger)), chain.neow3j);

        assertThat(read(instance, "getOwner").getByteArray()).isEqualTo(owner.getScriptHash().toLittleEndianArray());
    }

    private static ContractParameter nextEpoch() throws Throwable {
        return integer(read(chain.credits, "getEpoch").getInteger().add(BigInteger.ONE));
    }

    private static ContractParameter anyRoot() {
        byte[] root = new byte[32];
        root[0] = 1;
        return byteArray(root);
    }

    private static void assertDeployAborts(ContractParameter data) throws Throwable {
        assertAborted(sendDeploy(CityCredits.class, stranger, data, chain.neow3j), "CC: bad owner", chain.neow3j);
    }
}
