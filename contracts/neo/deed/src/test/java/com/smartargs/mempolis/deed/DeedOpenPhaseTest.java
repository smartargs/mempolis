package com.smartargs.mempolis.deed;

import com.smartargs.mempolis.guestbook.FoundersGuestbook;
import com.smartargs.mempolis.testkit.ReadyNeoExpressContainer;
import com.smartargs.mempolis.testkit.TestHelper;
import io.neow3j.contract.GasToken;
import io.neow3j.contract.SmartContract;
import io.neow3j.crypto.ECKeyPair;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.NeoApplicationLog;
import io.neow3j.protocol.core.response.Notification;
import io.neow3j.protocol.core.stackitem.StackItem;
import io.neow3j.test.ContractTest;
import io.neow3j.test.ContractTestExtension;
import io.neow3j.test.DeployConfig;
import io.neow3j.test.DeployConfiguration;
import io.neow3j.test.DeployContext;
import io.neow3j.transaction.AccountSigner;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;
import io.neow3j.wallet.Account;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static com.smartargs.mempolis.deed.DeedFixture.CHOICE_PRICE;
import static com.smartargs.mempolis.deed.DeedFixture.NEWCOMER_FEE;
import static com.smartargs.mempolis.deed.DeedFixture.PRIME_PRICE;
import static com.smartargs.mempolis.deed.DeedFixture.RESTYLE_PRICE;
import static com.smartargs.mempolis.deed.DeedFixture.RING_0_CHOICE;
import static com.smartargs.mempolis.deed.DeedFixture.RING_0_PRIME;
import static com.smartargs.mempolis.deed.DeedFixture.RING_0_STANDARD;
import static com.smartargs.mempolis.deed.DeedFixture.RING_0_VIP;
import static com.smartargs.mempolis.deed.DeedFixture.RING_2_CHOICE;
import static com.smartargs.mempolis.deed.DeedFixture.STANDARD;
import static com.smartargs.mempolis.deed.DeedFixture.STYLES;
import static com.smartargs.mempolis.deed.DeedFixture.chosen;
import static com.smartargs.mempolis.deed.DeedFixture.deedVoucher;
import static com.smartargs.mempolis.deed.DeedFixture.deployData;
import static com.smartargs.mempolis.deed.DeedFixture.info;
import static com.smartargs.mempolis.deed.DeedFixture.lots;
import static com.smartargs.mempolis.deed.DeedFixture.newcomer;
import static com.smartargs.mempolis.deed.DeedFixture.pay;
import static com.smartargs.mempolis.deed.DeedFixture.restyle;
import static com.smartargs.mempolis.deed.DeedFixture.setPrices;
import static com.smartargs.mempolis.deed.DeedFixture.setUpCity;
import static com.smartargs.mempolis.testkit.TestHelper.assertAborted;
import static com.smartargs.mempolis.testkit.TestHelper.assertHalted;
import static com.smartargs.mempolis.testkit.TestHelper.deploy;
import static com.smartargs.mempolis.testkit.TestHelper.fundedAccount;
import static com.smartargs.mempolis.testkit.TestHelper.ownerAndGate;
import static com.smartargs.mempolis.testkit.TestHelper.read;
import static com.smartargs.mempolis.testkit.TestHelper.send;
import static com.smartargs.mempolis.testkit.TestHelper.sendTwice;
import static com.smartargs.mempolis.testkit.TestHelper.voucher;
import static io.neow3j.types.ContractParameter.any;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.byteArray;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;
import static io.neow3j.types.ContractParameter.string;
import static org.assertj.core.api.Assertions.assertThat;

@ContractTest(blockTime = 1, contracts = {FoundersGuestbook.class, MempolisDeed.class}, configFile = TestHelper.NODE_CONFIG)
public class DeedOpenPhaseTest {

    @RegisterExtension
    private static final ContractTestExtension ext = new ContractTestExtension(new ReadyNeoExpressContainer());

    private static final Account owner = Account.create();
    private static final Account treasury = Account.create();
    private static final ECKeyPair gate = Account.create().getECKeyPair();

    private static Neow3j neow3j;
    private static SmartContract deed;
    private static SmartContract guestbook;

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
        assertHalted(send(guestbook, "close", owner), neow3j);
        setUpCity(deed, owner, neow3j);
        openCity(deed);
    }

    @Test
    void claim_withAVoucher_mintsTheNextQueuedLotInTheFreeStyle() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);
        BigInteger lot = read(deed, "nextFreeLot").getInteger();
        BigInteger supply = read(deed, "totalSupply").getInteger();

        NeoApplicationLog.Execution execution = assertHalted(freeClaim(deed, wallet), neow3j);

        BigInteger number = execution.getStack().get(0).getInteger();
        assertThat(number).isEqualTo(supply.add(BigInteger.ONE));
        assertThat(read(deed, "numberOf", hash160(wallet)).getInteger()).isEqualTo(number);
        assertThat(read(deed, "balanceOf", hash160(wallet)).getInteger()).isEqualTo(BigInteger.ONE);
        assertThat(read(deed, "claimOn", integer(lot)).getInteger()).isEqualTo(number);
        assertThat(read(deed, "nextFreeLot").getInteger()).isNotEqualTo(lot);
        List<StackItem> held = read(deed, "deedAt", integer(number)).getList();
        assertThat(held.get(0).getInteger()).isEqualTo(number);
        assertThat(held.get(1).getAddress()).isEqualTo(wallet.getAddress());
        assertThat(held.get(2).getInteger()).isEqualTo(lot);
        assertThat(held.get(3).getInteger()).isZero();
        assertThat(held.get(4).getInteger()).isZero();
        assertThat(held.get(5).getValue()).isNull();
        assertThat(held.get(6).getValue()).isNull();
        assertThat(read(deed, "ownerOf", byteArray(tokenId(number))).getAddress()).isEqualTo(wallet.getAddress());
    }

    @Test
    void claim_firesANep11TransferFromNobodyAndClaimed() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);

        NeoApplicationLog.Execution execution = assertHalted(freeClaim(deed, wallet), neow3j);

        BigInteger number = execution.getStack().get(0).getInteger();
        Notification transfer = execution.getNotifications().get(0);
        assertThat(transfer.getEventName()).isEqualTo("Transfer");
        assertThat(transfer.getState().getList().get(0).getValue()).isNull();
        assertThat(transfer.getState().getList().get(1).getAddress()).isEqualTo(wallet.getAddress());
        assertThat(transfer.getState().getList().get(2).getInteger()).isEqualTo(BigInteger.ONE);
        assertThat(transfer.getState().getList().get(3).getByteArray()).isEqualTo(tokenId(number));
        Notification claimed = execution.getNotifications().get(1);
        assertThat(claimed.getEventName()).isEqualTo("Claimed");
        assertThat(claimed.getState().getList().get(1).getInteger()).isEqualTo(number);
    }

    @Test
    void claim_withoutAVoucher_aborts() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);

        assertAborted(send(deed, "claim", wallet, hash160(wallet), any(null)), "DD: bad voucher", neow3j);
    }

    @Test
    void claim_withAVoucherOfTheWrongLength_aborts() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);

        assertAborted(send(deed, "claim", wallet, hash160(wallet), string("AAAA")), "DD: bad voucher", neow3j);
    }

    @Test
    void claim_withAVoucherFromAnotherKey_aborts() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);
        ContractParameter forged = deedVoucher(neow3j, deed.getScriptHash(), wallet.getScriptHash(), Account.create().getECKeyPair());

        assertAborted(send(deed, "claim", wallet, hash160(wallet), forged), "DD: bad voucher", neow3j);
    }

    @Test
    void claim_withAnotherWalletsVoucher_aborts() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);
        ContractParameter theirs = deedVoucher(neow3j, deed.getScriptHash(), Account.create().getScriptHash(), gate);

        assertAborted(send(deed, "claim", wallet, hash160(wallet), theirs), "DD: bad voucher", neow3j);
    }

    @Test
    void claim_withAVoucherSignedWithoutTheNetwork_aborts() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);
        ContractParameter anyNetwork = voucher(deed.getScriptHash(), wallet.getScriptHash(), gate);

        assertAborted(send(deed, "claim", wallet, hash160(wallet), anyNetwork), "DD: bad voucher", neow3j);
    }

    @Test
    void payingForATakenLot_withTheLotAsRawBytes_stillAborts() throws Throwable {
        int lot = RING_0_CHOICE + 1;
        assertHalted(pay(neow3j, deed, fundedAccount(neow3j, ext), CHOICE_PRICE, chosen(lot, 0)), neow3j);

        ContractParameter asBytes = array(integer(2), byteArray(new byte[]{(byte) lot, 0}), integer(0));
        ContractParameter canonical = array(integer(2), byteArray(new byte[]{(byte) lot}), byteArray(new byte[]{1}));
        assertAborted(pay(neow3j, deed, fundedAccount(neow3j, ext), CHOICE_PRICE, asBytes), "DD: lot taken", neow3j);
        assertAborted(pay(neow3j, deed, fundedAccount(neow3j, ext), CHOICE_PRICE, canonical), "DD: lot taken", neow3j);
    }

    @Test
    void payingForALot_withTheLotAndStyleAsRawBytes_mintsThemAsNumbers() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);
        int lot = RING_0_PRIME + 1;

        assertHalted(pay(neow3j, deed, wallet, PRIME_PRICE, array(integer(2), byteArray(new byte[]{(byte) lot, 0}), byteArray(new byte[]{2, 0}))), neow3j);

        BigInteger number = read(deed, "numberOf", hash160(wallet)).getInteger();
        assertThat(read(deed, "claimOn", integer(lot)).getInteger()).isEqualTo(number);
        assertThat(read(deed, "deedAt", integer(number)).getList().get(3).getInteger()).isEqualTo(BigInteger.TWO);
    }

    @Test
    void claim_withAGuestbookVoucherFromTheSameGate_aborts() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);
        ContractParameter guestbookVoucher = voucher(guestbook.getScriptHash(), wallet.getScriptHash(), gate);

        assertAborted(send(deed, "claim", wallet, hash160(wallet), guestbookVoucher), "DD: bad voucher", neow3j);
    }

    @Test
    void claim_signedByAnotherWallet_aborts() throws Throwable {
        Account wallet = Account.create();
        Account forger = fundedAccount(neow3j, ext);

        assertAborted(send(deed, "claim", forger, hash160(wallet), deedVoucher(neow3j, deed.getScriptHash(), wallet.getScriptHash(), gate)),
                "DD: no witness", neow3j);
    }

    @Test
    void claim_forTheZeroWallet_aborts() throws Throwable {
        assertAborted(send(deed, "claim", fundedAccount(neow3j, ext), hash160(Hash160.ZERO), any(null)), "DD: bad wallet", neow3j);
    }

    @Test
    void claim_twice_abortsSoEachWalletHasOneDeed() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);
        assertHalted(freeClaim(deed, wallet), neow3j);

        assertAborted(freeClaim(deed, wallet), "DD: already claimed", neow3j);
        assertAborted(pay(neow3j, deed, wallet, NEWCOMER_FEE, newcomer()), "DD: already claimed", neow3j);
        assertAborted(pay(neow3j, deed, wallet, CHOICE_PRICE, chosen(RING_0_CHOICE + 1, 0)), "DD: already claimed", neow3j);
    }

    @Test
    void claim_forTwoWalletsInOneTransaction_aborts() throws Throwable {
        Account first = fundedAccount(neow3j, ext);
        Account second = fundedAccount(neow3j, ext);

        assertAborted(sendTwice(neow3j, deed, "claim",
                List.of(hash160(first), deedVoucher(neow3j, deed.getScriptHash(), first.getScriptHash(), gate)),
                List.of(hash160(second), deedVoucher(neow3j, deed.getScriptHash(), second.getScriptHash(), gate)),
                AccountSigner.calledByEntry(first), AccountSigner.calledByEntry(second)), "DD: re-entry", neow3j);
    }

    @Test
    void payingTheNewcomerFee_mintsTheNextQueuedLotAndForwardsTheFeeToTheTreasury() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);
        BigInteger lot = read(deed, "nextFreeLot").getInteger();
        BigInteger treasuryBefore = gasOf(treasury.getScriptHash());

        assertHalted(pay(neow3j, deed, wallet, NEWCOMER_FEE, newcomer()), neow3j);

        BigInteger number = read(deed, "numberOf", hash160(wallet)).getInteger();
        assertThat(read(deed, "deedAt", integer(number)).getList().get(2).getInteger()).isEqualTo(lot);
        assertThat(gasOf(treasury.getScriptHash())).isEqualTo(treasuryBefore.add(NEWCOMER_FEE));
        assertThat(gasOf(deed.getScriptHash())).isZero();
    }

    @Test
    void payingTheNewcomerFee_withTheWrongAmount_aborts() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);

        assertAborted(pay(neow3j, deed, wallet, NEWCOMER_FEE.subtract(BigInteger.ONE), newcomer()), "DD: wrong amount", neow3j);
        assertAborted(pay(neow3j, deed, wallet, NEWCOMER_FEE.add(BigInteger.ONE), newcomer()), "DD: wrong amount", neow3j);
    }

    @Test
    void payingForAChoiceLot_mintsThatLotInTheChosenStyleAndForwardsThePrice() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);
        BigInteger treasuryBefore = gasOf(treasury.getScriptHash());

        assertHalted(pay(neow3j, deed, wallet, CHOICE_PRICE, chosen(RING_0_CHOICE, 3)), neow3j);

        BigInteger number = read(deed, "numberOf", hash160(wallet)).getInteger();
        List<StackItem> held = read(deed, "deedAt", integer(number)).getList();
        assertThat(held.get(2).getInteger()).isEqualTo(BigInteger.valueOf(RING_0_CHOICE));
        assertThat(held.get(3).getInteger()).isEqualTo(BigInteger.valueOf(3));
        assertThat(gasOf(treasury.getScriptHash())).isEqualTo(treasuryBefore.add(CHOICE_PRICE));
        assertThat(gasOf(deed.getScriptHash())).isZero();
    }

    @Test
    void payingForAPrimeLot_mintsItAtThePrimePrice() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);

        assertAborted(pay(neow3j, deed, wallet, CHOICE_PRICE, chosen(RING_0_PRIME, 1)), "DD: wrong amount", neow3j);
        assertHalted(pay(neow3j, deed, wallet, PRIME_PRICE, chosen(RING_0_PRIME, 1)), neow3j);

        assertThat(read(deed, "claimOn", integer(RING_0_PRIME)).getInteger()).isEqualTo(read(deed, "numberOf", hash160(wallet)).getInteger());
    }

    @Test
    void payingForATakenLot_aborts() throws Throwable {
        Account first = fundedAccount(neow3j, ext);
        Account second = fundedAccount(neow3j, ext);
        int lot = RING_0_CHOICE + 2;
        assertHalted(pay(neow3j, deed, first, CHOICE_PRICE, chosen(lot, 0)), neow3j);

        assertAborted(pay(neow3j, deed, second, CHOICE_PRICE, chosen(lot, 0)), "DD: lot taken", neow3j);
    }

    @Test
    void payingForAVipLot_abortsBecauseVipLotsAreReserved() throws Throwable {
        assertAborted(pay(neow3j, deed, fundedAccount(neow3j, ext), PRIME_PRICE, chosen(RING_0_VIP, 0)), "DD: vip reserved", neow3j);
    }

    @Test
    void payingForAStandardLot_abortsBecauseOnlyTheQueueHandsThemOut() throws Throwable {
        assertAborted(pay(neow3j, deed, fundedAccount(neow3j, ext), CHOICE_PRICE, chosen(RING_0_STANDARD, 0)),
                "DD: lot not for sale", neow3j);
    }

    @Test
    void payingForAnUnregisteredLot_aborts() throws Throwable {
        assertAborted(pay(neow3j, deed, fundedAccount(neow3j, ext), CHOICE_PRICE, chosen(41, 0)), "DD: bad lot", neow3j);
        assertAborted(pay(neow3j, deed, fundedAccount(neow3j, ext), CHOICE_PRICE, chosen(-1, 0)), "DD: bad lot", neow3j);
    }

    @Test
    void payingForALotInAnUnopenedRing_aborts() throws Throwable {
        assertAborted(pay(neow3j, deed, fundedAccount(neow3j, ext), CHOICE_PRICE, chosen(RING_2_CHOICE, 0)), "DD: ring closed", neow3j);
    }

    @Test
    void payingForALot_inAStyleThatDoesNotExist_aborts() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);

        assertAborted(pay(neow3j, deed, wallet, CHOICE_PRICE, chosen(RING_0_CHOICE + 3, STYLES)), "DD: bad style", neow3j);
        assertAborted(pay(neow3j, deed, wallet, CHOICE_PRICE, chosen(RING_0_CHOICE + 3, -1)), "DD: bad style", neow3j);
    }

    @Test
    void paying_withoutData_aborts() throws Throwable {
        assertAborted(pay(neow3j, deed, fundedAccount(neow3j, ext), NEWCOMER_FEE, any(null)), "DD: bad data", neow3j);
    }

    @Test
    void paying_withMalformedData_aborts() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);

        assertAborted(pay(neow3j, deed, wallet, NEWCOMER_FEE, array()), "DD: bad data", neow3j);
        assertAborted(pay(neow3j, deed, wallet, NEWCOMER_FEE, array(integer(9))), "DD: bad data", neow3j);
        assertAborted(pay(neow3j, deed, wallet, NEWCOMER_FEE, array(integer(1), integer(1))), "DD: bad data", neow3j);
        assertAborted(pay(neow3j, deed, wallet, CHOICE_PRICE, array(integer(2), integer(RING_0_CHOICE))), "DD: bad data", neow3j);
        assertAborted(pay(neow3j, deed, wallet, RESTYLE_PRICE, array(integer(3))), "DD: bad data", neow3j);
    }

    @Test
    void payingToRestyle_changesTheStyleAndForwardsThePrice() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);
        assertHalted(freeClaim(deed, wallet), neow3j);
        BigInteger number = read(deed, "numberOf", hash160(wallet)).getInteger();
        BigInteger treasuryBefore = gasOf(treasury.getScriptHash());

        NeoApplicationLog.Execution execution = assertHalted(pay(neow3j, deed, wallet, RESTYLE_PRICE, restyle(2)), neow3j);

        assertThat(read(deed, "deedAt", integer(number)).getList().get(3).getInteger()).isEqualTo(BigInteger.TWO);
        assertThat(gasOf(treasury.getScriptHash())).isEqualTo(treasuryBefore.add(RESTYLE_PRICE));
        assertThat(execution.getNotifications().stream().map(Notification::getEventName)).contains("StyleChanged");
    }

    @Test
    void payingToRestyle_toTheSameStyle_aborts() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);
        assertHalted(freeClaim(deed, wallet), neow3j);

        assertAborted(pay(neow3j, deed, wallet, RESTYLE_PRICE, restyle(0)), "DD: same style", neow3j);
    }

    @Test
    void payingToRestyle_withoutADeed_aborts() throws Throwable {
        assertAborted(pay(neow3j, deed, fundedAccount(neow3j, ext), RESTYLE_PRICE, restyle(1)), "DD: no deed", neow3j);
    }

    @Test
    void payingToRestyle_toAStyleThatDoesNotExist_aborts() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);
        assertHalted(freeClaim(deed, wallet), neow3j);

        assertAborted(pay(neow3j, deed, wallet, RESTYLE_PRICE, restyle(STYLES)), "DD: bad style", neow3j);
    }

    @Test
    void payingToRestyle_withTheWrongAmount_aborts() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);
        assertHalted(freeClaim(deed, wallet), neow3j);

        assertAborted(pay(neow3j, deed, wallet, RESTYLE_PRICE.add(BigInteger.ONE), restyle(1)), "DD: wrong amount", neow3j);
    }

    @Test
    void setLook_storesTheLookWhichCreatesTheResident() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);
        assertHalted(freeClaim(deed, wallet), neow3j);
        byte[] look = {1, 2, 3, 4};

        NeoApplicationLog.Execution execution = assertHalted(send(deed, "setLook", wallet, hash160(wallet), byteArray(look)), neow3j);

        BigInteger number = read(deed, "numberOf", hash160(wallet)).getInteger();
        assertThat(read(deed, "deedAt", integer(number)).getList().get(5).getByteArray()).isEqualTo(look);
        assertThat(execution.getNotifications().get(0).getEventName()).isEqualTo("LookChanged");
    }

    @Test
    void setLook_withAnEmptyOrOversizedLook_aborts() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);
        assertHalted(freeClaim(deed, wallet), neow3j);

        assertAborted(send(deed, "setLook", wallet, hash160(wallet), byteArray(new byte[0])), "DD: bad look", neow3j);
        assertAborted(send(deed, "setLook", wallet, hash160(wallet), byteArray(new byte[17])), "DD: bad look", neow3j);
    }

    @Test
    void setLook_withoutADeed_aborts() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);

        assertAborted(send(deed, "setLook", wallet, hash160(wallet), byteArray(new byte[]{1})), "DD: no deed", neow3j);
    }

    @Test
    void setLook_signedByAnotherWallet_aborts() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);
        assertHalted(freeClaim(deed, wallet), neow3j);

        assertAborted(send(deed, "setLook", fundedAccount(neow3j, ext), hash160(wallet), byteArray(new byte[]{1})),
                "DD: no witness", neow3j);
    }

    @Test
    void setStatus_storesTheLineAndAnEmptyLineClearsIt() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);
        assertHalted(freeClaim(deed, wallet), neow3j);
        BigInteger number = read(deed, "numberOf", hash160(wallet)).getInteger();

        assertHalted(send(deed, "setStatus", wallet, hash160(wallet), string("gm from the Stacks")), neow3j);
        assertThat(read(deed, "deedAt", integer(number)).getList().get(6).getString()).isEqualTo("gm from the Stacks");

        assertHalted(send(deed, "setStatus", wallet, hash160(wallet), string("")), neow3j);
        assertThat(read(deed, "deedAt", integer(number)).getList().get(6).getValue()).isNull();
    }

    @Test
    void setStatus_longerThan112Bytes_aborts() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);
        assertHalted(freeClaim(deed, wallet), neow3j);
        String tooLong = "€".repeat(38);
        assertThat(tooLong.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(112);

        assertAborted(send(deed, "setStatus", wallet, hash160(wallet), string(tooLong)), "DD: bad status", neow3j);
    }

    @Test
    void setStatus_withoutADeed_aborts() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);

        assertAborted(send(deed, "setStatus", wallet, hash160(wallet), string("hi")), "DD: no deed", neow3j);
    }

    @Test
    void properties_nameTheDeedAndItsLot() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);
        BigInteger number = assertHalted(freeClaim(deed, wallet), neow3j).getStack().get(0).getInteger();

        Map<StackItem, StackItem> properties = read(deed, "properties", byteArray(tokenId(number))).getMap();

        Map<String, StackItem> byName = new java.util.HashMap<>();
        properties.forEach((k, v) -> byName.put(k.getString(), v));
        assertThat(byName.get("name").getString()).isEqualTo("Mempolis Deed #" + number);
        assertThat(byName.get("lot").getInteger()).isEqualTo(read(deed, "deedAt", integer(number)).getList().get(2).getInteger());
    }

    @Test
    void deeds_returnsAPageInClaimOrder() throws Throwable {
        assertHalted(freeClaim(deed, fundedAccount(neow3j, ext)), neow3j);
        assertHalted(freeClaim(deed, fundedAccount(neow3j, ext)), neow3j);

        List<StackItem> page = read(deed, "deeds", integer(1), integer(64)).getList();

        assertThat(BigInteger.valueOf(page.size())).isEqualTo(read(deed, "totalSupply").getInteger());
        for (int i = 0; i < page.size(); i++) {
            assertThat(page.get(i).getList().get(0).getInteger()).isEqualTo(BigInteger.valueOf(i + 1));
        }
        assertThat(read(deed, "deeds", integer(2), integer(1)).getList()).hasSize(1);
    }

    @Test
    void transfer_ofAClaimedDeed_aborts() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);
        BigInteger number = assertHalted(freeClaim(deed, wallet), neow3j).getStack().get(0).getInteger();

        assertAborted(send(deed, "transfer", wallet, hash160(fundedAccount(neow3j, ext)), byteArray(tokenId(number)), any(null)),
                "DD: soulbound", neow3j);
    }

    @Test
    void freeClaims_openTheRingOfTheirLotWhenTheQueueReachesIt() throws Throwable {
        SmartContract fresh = smallCity(new byte[]{info(STANDARD, 0), info(STANDARD, 1)}, 1, 2);

        assertHalted(freeClaim(fresh, fundedAccount(neow3j, ext)), neow3j);
        assertThat(read(fresh, "openRing").getInteger()).isZero();

        NeoApplicationLog.Execution execution = assertHalted(freeClaim(fresh, fundedAccount(neow3j, ext)), neow3j);

        assertThat(read(fresh, "openRing").getInteger()).isEqualTo(BigInteger.ONE);
        assertThat(execution.getNotifications().stream().map(Notification::getEventName)).contains("RingOpened");
    }

    @Test
    void freeClaims_skipALotQueuedTwiceInsteadOfHandingItOutAgain() throws Throwable {
        SmartContract fresh = smallCity(new byte[]{info(STANDARD, 0), info(STANDARD, 0)}, 1, 1, 2);
        Account first = fundedAccount(neow3j, ext), second = fundedAccount(neow3j, ext);

        assertHalted(freeClaim(fresh, first), neow3j);
        assertThat(read(fresh, "nextFreeLot").getInteger()).isEqualTo(BigInteger.TWO);
        assertHalted(freeClaim(fresh, second), neow3j);

        assertThat(read(fresh, "claimOn", integer(1)).getInteger()).isEqualTo(read(fresh, "numberOf", hash160(first)).getInteger());
        assertThat(read(fresh, "claimOn", integer(2)).getInteger()).isEqualTo(read(fresh, "numberOf", hash160(second)).getInteger());
        assertThat(read(fresh, "nextFreeLot").getInteger()).isZero();
    }

    @Test
    void claim_onceTheQueueIsUsedUp_aborts() throws Throwable {
        SmartContract fresh = smallCity(new byte[]{info(STANDARD, 0)}, 1);
        assertHalted(freeClaim(fresh, fundedAccount(neow3j, ext)), neow3j);

        assertThat(read(fresh, "nextFreeLot").getInteger()).isZero();
        assertAborted(freeClaim(fresh, fundedAccount(neow3j, ext)), "DD: no free lot", neow3j);
    }

    @Test
    void paying_whenThatSaleIsStopped_aborts() throws Throwable {
        SmartContract fresh = smallCity(new byte[]{info(STANDARD, 0)}, 1);
        assertHalted(send(fresh, "setPrice", owner, integer(1), integer(0)), neow3j);

        assertAborted(pay(neow3j, fresh, fundedAccount(neow3j, ext), NEWCOMER_FEE, newcomer()), "DD: not for sale", neow3j);
    }

    private static SmartContract smallCity(byte[] infos, int... queue) throws Throwable {
        SmartContract fresh = deploy(MempolisDeed.class, fundedAccount(neow3j, ext),
                deployData(owner.getScriptHash(), gate, guestbook.getScriptHash(), treasury.getScriptHash(), STYLES), neow3j);
        assertHalted(send(fresh, "registerLots", owner, integer(1), byteArray(infos)), neow3j);
        assertHalted(send(fresh, "queueLots", owner, integer(1), lots(queue)), neow3j);
        setPrices(fresh, owner, neow3j);
        openCity(fresh);
        return fresh;
    }

    private static void openCity(SmartContract contract) throws Throwable {
        assertHalted(send(contract, "setPhase", owner, integer(1)), neow3j);
        assertHalted(send(contract, "setPhase", owner, integer(2)), neow3j);
    }

    private static io.neow3j.types.Hash256 freeClaim(SmartContract contract, Account wallet) throws Throwable {
        return send(contract, "claim", wallet, hash160(wallet), deedVoucher(neow3j, contract.getScriptHash(), wallet.getScriptHash(), gate));
    }

    private static BigInteger gasOf(Hash160 holder) throws Throwable {
        return new GasToken(neow3j).getBalanceOf(holder);
    }

    private static byte[] tokenId(BigInteger number) {
        return io.neow3j.utils.BigIntegers.toLittleEndianByteArray(number);
    }
}
