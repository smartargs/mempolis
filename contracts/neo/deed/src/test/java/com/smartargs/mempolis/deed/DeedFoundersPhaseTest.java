package com.smartargs.mempolis.deed;

import com.smartargs.mempolis.guestbook.FoundersGuestbook;
import com.smartargs.mempolis.testkit.ReadyNeoExpressContainer;
import com.smartargs.mempolis.testkit.TestHelper;
import io.neow3j.contract.SmartContract;
import io.neow3j.crypto.ECKeyPair;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.stackitem.StackItem;
import io.neow3j.test.ContractTest;
import io.neow3j.test.ContractTestExtension;
import io.neow3j.test.DeployConfig;
import io.neow3j.test.DeployConfiguration;
import io.neow3j.test.DeployContext;
import io.neow3j.types.Hash160;
import io.neow3j.wallet.Account;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.math.BigInteger;
import java.util.List;

import static com.smartargs.mempolis.deed.DeedFixture.CHOICE_PRICE;
import static com.smartargs.mempolis.deed.DeedFixture.NEWCOMER_FEE;
import static com.smartargs.mempolis.deed.DeedFixture.RING_0_CHOICE;
import static com.smartargs.mempolis.deed.DeedFixture.STYLES;
import static com.smartargs.mempolis.deed.DeedFixture.chosen;
import static com.smartargs.mempolis.deed.DeedFixture.deedVoucher;
import static com.smartargs.mempolis.deed.DeedFixture.deployData;
import static com.smartargs.mempolis.deed.DeedFixture.newcomer;
import static com.smartargs.mempolis.deed.DeedFixture.pay;
import static com.smartargs.mempolis.deed.DeedFixture.setUpCity;
import static com.smartargs.mempolis.testkit.TestHelper.assertAborted;
import static com.smartargs.mempolis.testkit.TestHelper.assertHalted;
import static com.smartargs.mempolis.testkit.TestHelper.fundedAccount;
import static com.smartargs.mempolis.testkit.TestHelper.ownerAndGate;
import static com.smartargs.mempolis.testkit.TestHelper.read;
import static com.smartargs.mempolis.testkit.TestHelper.send;
import static com.smartargs.mempolis.testkit.TestHelper.voucher;
import static io.neow3j.types.ContractParameter.any;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;
import static org.assertj.core.api.Assertions.assertThat;

@ContractTest(blockTime = 1, contracts = {FoundersGuestbook.class, MempolisDeed.class}, configFile = TestHelper.NODE_CONFIG)
public class DeedFoundersPhaseTest {

    @RegisterExtension
    private static final ContractTestExtension ext = new ContractTestExtension(new ReadyNeoExpressContainer());

    private static final Account owner = Account.create();
    private static final Account treasury = Account.create();
    private static final ECKeyPair gate = Account.create().getECKeyPair();

    private static Neow3j neow3j;
    private static SmartContract deed;
    private static SmartContract guestbook;
    private static Account firstFounder;
    private static Account secondFounder;
    private static Account thirdFounder;

    @DeployConfig(FoundersGuestbook.class)
    public static DeployConfiguration configureGuestbook(DeployContext ctx) {
        DeployConfiguration config = new DeployConfiguration();
        config.setDeployParam(ownerAndGate(owner.getScriptHash(), gate));
        return config;
    }

    @DeployConfig(MempolisDeed.class)
    public static DeployConfiguration configureDeed(DeployContext ctx) {
        DeployConfiguration config = new DeployConfiguration();
        Hash160 book = ctx.getDeployedContract(FoundersGuestbook.class).getScriptHash();
        config.setDeployParam(deployData(owner.getScriptHash(), gate, book, treasury.getScriptHash(), STYLES));
        return config;
    }

    @BeforeAll
    static void setUp() throws Throwable {
        neow3j = ext.getNeow3j();
        neow3j.allowTransmissionOnFault();
        deed = ext.getDeployedContract(MempolisDeed.class);
        guestbook = ext.getDeployedContract(FoundersGuestbook.class);
        TestHelper.fund(neow3j, ext, owner.getScriptHash());
        firstFounder = signedFounder();
        secondFounder = signedFounder();
        thirdFounder = signedFounder();
        assertHalted(send(guestbook, "close", owner), neow3j);
        setUpCity(deed, owner, neow3j);
        assertHalted(send(deed, "setPhase", owner, integer(1)), neow3j);
    }

    @Test
    void setPhase_opensTheFoundersPhaseOnceTheGuestbookIsClosed() throws Throwable {
        assertThat(read(deed, "phase").getInteger()).isEqualTo(BigInteger.ONE);
    }

    @Test
    void setPhase_toTheSamePhaseAgain_aborts() throws Throwable {
        assertAborted(send(deed, "setPhase", owner, integer(1)), "DD: bad phase", neow3j);
    }

    @Test
    void claim_byAFounder_needsNoVoucherAndRecordsTheFounderNumber() throws Throwable {
        BigInteger founderNumber = read(guestbook, "numberOf", hash160(firstFounder)).getInteger();
        BigInteger lot = read(deed, "nextFreeLot").getInteger();

        BigInteger number = assertHalted(send(deed, "claim", firstFounder, hash160(firstFounder), any(null)), neow3j)
                .getStack().get(0).getInteger();

        List<StackItem> held = read(deed, "deedAt", integer(number)).getList();
        assertThat(held.get(2).getInteger()).isEqualTo(lot);
        assertThat(held.get(3).getInteger()).isZero();
        assertThat(held.get(4).getInteger()).isEqualTo(founderNumber);
    }

    @Test
    void claim_byAWalletThatIsNoFounder_abortsEvenWithAVoucher() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);

        assertAborted(send(deed, "claim", wallet, hash160(wallet), deedVoucher(neow3j, deed.getScriptHash(), wallet.getScriptHash(), gate)),
                "DD: founders only", neow3j);
    }

    @Test
    void payingForANewcomerClaim_byAWalletThatIsNoFounder_aborts() throws Throwable {
        assertAborted(pay(neow3j, deed, fundedAccount(neow3j, ext), NEWCOMER_FEE, newcomer()), "DD: founders only", neow3j);
    }

    @Test
    void payingForAChosenClaim_byAWalletThatIsNoFounder_aborts() throws Throwable {
        assertAborted(pay(neow3j, deed, fundedAccount(neow3j, ext), CHOICE_PRICE, chosen(RING_0_CHOICE, 2)),
                "DD: founders only", neow3j);
    }

    @Test
    void payingForAChosenClaim_byAFounder_mintsTheChosenLotAndStyle() throws Throwable {
        assertHalted(pay(neow3j, deed, secondFounder, CHOICE_PRICE, chosen(RING_0_CHOICE, 2)), neow3j);

        BigInteger number = read(deed, "numberOf", hash160(secondFounder)).getInteger();
        List<StackItem> held = read(deed, "deedAt", integer(number)).getList();
        assertThat(held.get(2).getInteger()).isEqualTo(BigInteger.valueOf(RING_0_CHOICE));
        assertThat(held.get(3).getInteger()).isEqualTo(BigInteger.TWO);
        assertThat(held.get(4).getInteger()).isEqualTo(read(guestbook, "numberOf", hash160(secondFounder)).getInteger());
    }

    @Test
    void payingForANewcomerClaim_byAFounder_isAllowed() throws Throwable {
        assertHalted(pay(neow3j, deed, thirdFounder, NEWCOMER_FEE, newcomer()), neow3j);

        assertThat(read(deed, "balanceOf", hash160(thirdFounder)).getInteger()).isEqualTo(BigInteger.ONE);
    }

    private static Account signedFounder() throws Throwable {
        Account founder = fundedAccount(neow3j, ext);
        assertHalted(send(guestbook, "sign", founder, hash160(founder),
                voucher(guestbook.getScriptHash(), founder.getScriptHash(), gate)), neow3j);
        return founder;
    }
}
