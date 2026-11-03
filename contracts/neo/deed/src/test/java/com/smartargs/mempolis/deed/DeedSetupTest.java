package com.smartargs.mempolis.deed;

import com.smartargs.mempolis.deed.testsupport.LyingToken;
import com.smartargs.mempolis.deed.testsupport.UpdatedDeed;
import com.smartargs.mempolis.guestbook.FoundersGuestbook;
import com.smartargs.mempolis.testkit.ReadyNeoExpressContainer;
import com.smartargs.mempolis.testkit.TestHelper;
import io.neow3j.contract.SmartContract;
import io.neow3j.crypto.ECKeyPair;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.ContractManifest;
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
import io.neow3j.wallet.Account;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

import static com.smartargs.mempolis.deed.DeedFixture.CHOICE;
import static com.smartargs.mempolis.deed.DeedFixture.NEWCOMER_FEE;
import static com.smartargs.mempolis.deed.DeedFixture.PRICE_CHOICE;
import static com.smartargs.mempolis.deed.DeedFixture.STANDARD;
import static com.smartargs.mempolis.deed.DeedFixture.STYLES;
import static com.smartargs.mempolis.deed.DeedFixture.deedVoucher;
import static com.smartargs.mempolis.deed.DeedFixture.deployData;
import static com.smartargs.mempolis.deed.DeedFixture.info;
import static com.smartargs.mempolis.deed.DeedFixture.lots;
import static com.smartargs.mempolis.deed.DeedFixture.newcomer;
import static com.smartargs.mempolis.deed.DeedFixture.pay;
import static com.smartargs.mempolis.testkit.TestHelper.assertAborted;
import static com.smartargs.mempolis.testkit.TestHelper.assertHalted;
import static com.smartargs.mempolis.testkit.TestHelper.assertReadAborts;
import static com.smartargs.mempolis.testkit.TestHelper.deploy;
import static com.smartargs.mempolis.testkit.TestHelper.fundedAccount;
import static com.smartargs.mempolis.testkit.TestHelper.ownerAndGate;
import static com.smartargs.mempolis.testkit.TestHelper.read;
import static com.smartargs.mempolis.testkit.TestHelper.send;
import static com.smartargs.mempolis.testkit.TestHelper.sendDeploy;
import static com.smartargs.mempolis.testkit.TestHelper.sendTwice;
import static com.smartargs.mempolis.testkit.TestHelper.sendUpdate;
import static com.smartargs.mempolis.testkit.TestHelper.updateParams;
import static com.smartargs.mempolis.testkit.TestHelper.voucher;
import static io.neow3j.types.ContractParameter.any;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.byteArray;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;
import static io.neow3j.types.ContractParameter.publicKey;
import static org.assertj.core.api.Assertions.assertThat;

@ContractTest(blockTime = 1, contracts = {FoundersGuestbook.class, MempolisDeed.class, LyingToken.class},
        configFile = TestHelper.NODE_CONFIG)
public class DeedSetupTest {

    @RegisterExtension
    private static final ContractTestExtension ext = new ContractTestExtension(new ReadyNeoExpressContainer());

    private static final Account owner = Account.create();
    private static final Account treasury = Account.create();
    private static final ECKeyPair gate = Account.create().getECKeyPair();

    private static Neow3j neow3j;
    private static SmartContract deed;
    private static SmartContract guestbook;
    private static SmartContract liar;
    private static Account stranger;

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
        liar = ext.getDeployedContract(LyingToken.class);
        TestHelper.fund(neow3j, ext, owner.getScriptHash());
        stranger = fundedAccount(neow3j, ext);
    }

    @Test
    void deploy_storesItsSettingsAndStartsClosedAndEmpty() throws Throwable {
        assertThat(read(deed, "getOwner").getByteArray()).isEqualTo(owner.getScriptHash().toLittleEndianArray());
        assertThat(read(deed, "getGuestbook").getByteArray()).isEqualTo(guestbook.getScriptHash().toLittleEndianArray());
        assertThat(read(deed, "getTreasury").getByteArray()).isEqualTo(treasury.getScriptHash().toLittleEndianArray());
        assertThat(read(deed, "getGate").getHexString()).isEqualTo(io.neow3j.utils.Numeric.toHexStringNoPrefix(gate.getPublicKey().getEncoded(true)));
        assertThat(read(deed, "styleCount").getInteger()).isEqualTo(BigInteger.valueOf(STYLES));
        assertThat(read(deed, "phase").getInteger()).isZero();
        assertThat(read(deed, "totalSupply").getInteger()).isZero();
        assertThat(read(deed, "symbol").getString()).isEqualTo("DEED");
        assertThat(read(deed, "decimals").getInteger()).isZero();
    }

    @Test
    void deploy_withoutData_aborts() throws Throwable {
        assertAborted(sendDeploy(MempolisDeed.class, fundedAccount(neow3j, ext), any(null), neow3j), "DD: bad deploy data", neow3j);
    }

    @Test
    void deploy_withTooFewSettings_aborts() throws Throwable {
        ContractParameter data = array(hash160(owner), publicKey(gate.getPublicKey().getEncoded(true)));
        assertAborted(sendDeploy(MempolisDeed.class, fundedAccount(neow3j, ext), data, neow3j), "DD: bad deploy data", neow3j);
    }

    @Test
    void deploy_withAZeroOwner_aborts() throws Throwable {
        assertDeployAborts(Hash160.ZERO, gate, guestbook.getScriptHash(), treasury.getScriptHash(), STYLES, "DD: bad owner");
    }

    @Test
    void deploy_withAMalformedGate_aborts() throws Throwable {
        ContractParameter data = array(hash160(owner), byteArray(new byte[33]), hash160(guestbook.getScriptHash()),
                hash160(treasury), integer(STYLES));
        assertAborted(sendDeploy(MempolisDeed.class, fundedAccount(neow3j, ext), data, neow3j), "DD: bad gate", neow3j);
    }

    @Test
    void deploy_withAZeroGuestbook_aborts() throws Throwable {
        assertDeployAborts(owner.getScriptHash(), gate, Hash160.ZERO, treasury.getScriptHash(), STYLES, "DD: bad guestbook");
    }

    @Test
    void deploy_withAZeroTreasury_aborts() throws Throwable {
        assertDeployAborts(owner.getScriptHash(), gate, guestbook.getScriptHash(), Hash160.ZERO, STYLES, "DD: bad treasury");
    }

    @Test
    void deploy_withNoStyles_aborts() throws Throwable {
        assertDeployAborts(owner.getScriptHash(), gate, guestbook.getScriptHash(), treasury.getScriptHash(), 0, "DD: bad styles");
    }

    @Test
    void deploy_withTooManyStyles_aborts() throws Throwable {
        assertDeployAborts(owner.getScriptHash(), gate, guestbook.getScriptHash(), treasury.getScriptHash(), 33, "DD: bad styles");
    }

    @Test
    void registerLots_storesTierAndRingOfEachLotAndFiresTheRange() throws Throwable {
        SmartContract fresh = freshDeed();
        byte[] infos = {info(STANDARD, 0), info(CHOICE, 3), info(4, 15)};

        NeoApplicationLog.Execution execution = assertHalted(send(fresh, "registerLots", owner, integer(1), byteArray(infos)), neow3j);

        assertThat(read(fresh, "lotCount").getInteger()).isEqualTo(BigInteger.valueOf(3));
        assertThat(read(fresh, "tierOf", integer(2)).getInteger()).isEqualTo(BigInteger.valueOf(CHOICE));
        assertThat(read(fresh, "ringOf", integer(2)).getInteger()).isEqualTo(BigInteger.valueOf(3));
        assertThat(read(fresh, "tierOf", integer(3)).getInteger()).isEqualTo(BigInteger.valueOf(4));
        assertThat(read(fresh, "ringOf", integer(3)).getInteger()).isEqualTo(BigInteger.valueOf(15));
        assertThat(read(fresh, "tierOf", integer(4)).getInteger()).isZero();
        Notification registered = execution.getNotifications().get(0);
        assertThat(registered.getEventName()).isEqualTo("LotsRegistered");
        assertThat(registered.getState().getList().get(0).getInteger()).isEqualTo(BigInteger.ONE);
        assertThat(registered.getState().getList().get(1).getInteger()).isEqualTo(BigInteger.valueOf(3));
    }

    @Test
    void registerLots_appendsAfterTheLastLot() throws Throwable {
        SmartContract fresh = freshDeed();
        assertHalted(send(fresh, "registerLots", owner, integer(1), byteArray(new byte[]{info(STANDARD, 0)})), neow3j);

        assertHalted(send(fresh, "registerLots", owner, integer(2), byteArray(new byte[]{info(CHOICE, 1)})), neow3j);

        assertThat(read(fresh, "lotCount").getInteger()).isEqualTo(BigInteger.TWO);
        assertThat(read(fresh, "tierOf", integer(1)).getInteger()).isEqualTo(BigInteger.valueOf(STANDARD));
    }

    @Test
    void registerLots_overARegisteredLot_abortsSoNoLotEverChanges() throws Throwable {
        SmartContract fresh = freshDeed();
        assertHalted(send(fresh, "registerLots", owner, integer(1), byteArray(new byte[]{info(STANDARD, 0)})), neow3j);

        assertAborted(send(fresh, "registerLots", owner, integer(1), byteArray(new byte[]{info(CHOICE, 0)})),
                "DD: bad first lot", neow3j);

        assertThat(read(fresh, "tierOf", integer(1)).getInteger()).isEqualTo(BigInteger.valueOf(STANDARD));
    }

    @Test
    void registerLots_leavingAGap_aborts() throws Throwable {
        assertAborted(send(freshDeed(), "registerLots", owner, integer(2), byteArray(new byte[]{info(STANDARD, 0)})),
                "DD: bad first lot", neow3j);
    }

    @Test
    void registerLots_withNoLots_aborts() throws Throwable {
        assertAborted(send(deed, "registerLots", owner, integer(1), byteArray(new byte[0])), "DD: bad batch", neow3j);
    }

    @Test
    void registerLots_withMoreThan200Lots_aborts() throws Throwable {
        byte[] infos = new byte[201];
        java.util.Arrays.fill(infos, info(STANDARD, 0));
        assertAborted(send(deed, "registerLots", owner, integer(1), byteArray(infos)), "DD: bad batch", neow3j);
    }

    @Test
    void registerLots_withTierZero_aborts() throws Throwable {
        assertAborted(send(deed, "registerLots", owner, integer(1), byteArray(new byte[]{8})), "DD: bad lot info", neow3j);
    }

    @Test
    void registerLots_withTierFive_aborts() throws Throwable {
        assertAborted(send(deed, "registerLots", owner, integer(1), byteArray(new byte[]{5})), "DD: bad lot info", neow3j);
    }

    @Test
    void registerLots_withARingAbove15_aborts() throws Throwable {
        assertAborted(send(deed, "registerLots", owner, integer(1), byteArray(new byte[]{(byte) 129})), "DD: bad lot info", neow3j);
    }

    @Test
    void registerLots_byAStranger_aborts() throws Throwable {
        assertAborted(send(deed, "registerLots", stranger, integer(1), byteArray(new byte[]{info(STANDARD, 0)})),
                "DD: not owner", neow3j);
    }

    @Test
    void queueLots_appendsStandardLotsInOrder() throws Throwable {
        SmartContract fresh = freshDeed();
        assertHalted(send(fresh, "registerLots", owner, integer(1), byteArray(new byte[]{info(STANDARD, 0), info(STANDARD, 0), info(STANDARD, 1)})), neow3j);

        NeoApplicationLog.Execution execution = assertHalted(send(fresh, "queueLots", owner, integer(1), lots(3, 1)), neow3j);
        assertHalted(send(fresh, "queueLots", owner, integer(3), lots(2)), neow3j);

        assertThat(read(fresh, "queueLength").getInteger()).isEqualTo(BigInteger.valueOf(3));
        assertThat(read(fresh, "nextFreeLot").getInteger()).isEqualTo(BigInteger.valueOf(3));
        assertThat(read(fresh, "queueAt", integer(2)).getInteger()).isEqualTo(BigInteger.ONE);
        assertThat(read(fresh, "queueAt", integer(3)).getInteger()).isEqualTo(BigInteger.TWO);
        assertThat(read(fresh, "queueAt", integer(4)).getInteger()).isZero();
        assertThat(execution.getNotifications().get(0).getEventName()).isEqualTo("LotsQueued");
    }

    @Test
    void registerAndQueue_acrossChunkBoundaries_keepEveryLotAndPlace() throws Throwable {
        SmartContract fresh = freshDeed();
        byte[] first = new byte[150], second = new byte[130];
        for (int i = 0; i < first.length; i++) first[i] = info(i % 5 == 0 ? CHOICE : STANDARD, i % 16);
        for (int i = 0; i < second.length; i++) second[i] = info(STANDARD, (150 + i) % 16);
        assertHalted(send(fresh, "registerLots", owner, integer(1), byteArray(first)), neow3j);
        assertHalted(send(fresh, "registerLots", owner, integer(151), byteArray(second)), neow3j);
        int[] standard = java.util.stream.IntStream.rangeClosed(1, 280).filter(lot -> lot > 150 || (lot - 1) % 5 != 0).toArray();
        int[] head = java.util.Arrays.copyOfRange(standard, 0, 150), tail = java.util.Arrays.copyOfRange(standard, 150, standard.length);
        assertHalted(send(fresh, "queueLots", owner, integer(1), lots(head)), neow3j);
        assertHalted(send(fresh, "queueLots", owner, integer(151), lots(tail)), neow3j);

        for (int lot : new int[]{1, 2, 150, 151, 200, 201, 280}) {
            int tier = lot > 150 || (lot - 1) % 5 != 0 ? STANDARD : CHOICE, ring = (lot - 1) % 16;
            assertThat(read(fresh, "tierOf", integer(lot)).getInteger()).as("tier of " + lot).isEqualTo(BigInteger.valueOf(tier));
            assertThat(read(fresh, "ringOf", integer(lot)).getInteger()).as("ring of " + lot).isEqualTo(BigInteger.valueOf(ring));
        }
        for (int place : new int[]{1, 100, 101, 150, 151, 200, 201, standard.length}) {
            assertThat(read(fresh, "queueAt", integer(place)).getInteger()).as("place " + place).isEqualTo(BigInteger.valueOf(standard[place - 1]));
        }
        assertThat(read(fresh, "tierOf", integer(281)).getInteger()).isZero();
        assertThat(read(fresh, "queueAt", integer(standard.length + 1)).getInteger()).isZero();
    }

    @Test
    void queueLots_aChoiceLot_aborts() throws Throwable {
        SmartContract fresh = freshDeed();
        assertHalted(send(fresh, "registerLots", owner, integer(1), byteArray(new byte[]{info(CHOICE, 0)})), neow3j);

        assertAborted(send(fresh, "queueLots", owner, integer(1), lots(1)), "DD: not a standard lot", neow3j);
    }

    @Test
    void queueLots_anUnregisteredLot_aborts() throws Throwable {
        assertAborted(send(freshDeed(), "queueLots", owner, integer(1), lots(1)), "DD: not a standard lot", neow3j);
    }

    @Test
    void queueLots_atTheWrongPlace_aborts() throws Throwable {
        assertAborted(send(deed, "queueLots", owner, integer(2), lots(1)), "DD: bad first place", neow3j);
    }

    @Test
    void queueLots_withNoLots_aborts() throws Throwable {
        assertAborted(send(deed, "queueLots", owner, integer(1), array()), "DD: bad batch", neow3j);
    }

    @Test
    void queueLots_byAStranger_aborts() throws Throwable {
        assertAborted(send(deed, "queueLots", stranger, integer(1), lots(1)), "DD: not owner", neow3j);
    }

    @Test
    void setPrice_storesThePriceAndFiresIt() throws Throwable {
        SmartContract fresh = freshDeed();

        NeoApplicationLog.Execution execution = assertHalted(send(fresh, "setPrice", owner, integer(PRICE_CHOICE), integer(123)), neow3j);

        assertThat(read(fresh, "priceOf", integer(PRICE_CHOICE)).getInteger()).isEqualTo(BigInteger.valueOf(123));
        assertThat(execution.getNotifications().get(0).getEventName()).isEqualTo("PriceChanged");
    }

    @Test
    void setPrice_ofAnUnknownKind_aborts() throws Throwable {
        assertAborted(send(deed, "setPrice", owner, integer(0), integer(1)), "DD: bad price kind", neow3j);
        assertAborted(send(deed, "setPrice", owner, integer(5), integer(1)), "DD: bad price kind", neow3j);
    }

    @Test
    void setPrice_belowZero_aborts() throws Throwable {
        assertAborted(send(deed, "setPrice", owner, integer(PRICE_CHOICE), integer(-1)), "DD: bad amount", neow3j);
    }

    @Test
    void setPrice_byAStranger_aborts() throws Throwable {
        assertAborted(send(deed, "setPrice", stranger, integer(PRICE_CHOICE), integer(0)), "DD: not owner", neow3j);
    }

    @Test
    void setPrice_twiceInOneTransaction_aborts() throws Throwable {
        List<ContractParameter> price = List.of(integer(PRICE_CHOICE), integer(1));
        assertAborted(sendTwice(neow3j, deed, "setPrice", price, price, AccountSigner.calledByEntry(owner)),
                "DD: re-entry", neow3j);
    }

    @Test
    void openRingUpTo_opensRingsAndOnlyOutward() throws Throwable {
        SmartContract fresh = freshDeed();

        NeoApplicationLog.Execution execution = assertHalted(send(fresh, "openRingUpTo", owner, integer(2)), neow3j);

        assertThat(read(fresh, "openRing").getInteger()).isEqualTo(BigInteger.TWO);
        assertThat(execution.getNotifications().get(0).getEventName()).isEqualTo("RingOpened");
        assertAborted(send(fresh, "openRingUpTo", owner, integer(2)), "DD: bad ring", neow3j);
        assertAborted(send(fresh, "openRingUpTo", owner, integer(1)), "DD: bad ring", neow3j);
    }

    @Test
    void openRingUpTo_beyondRing15_aborts() throws Throwable {
        assertAborted(send(deed, "openRingUpTo", owner, integer(16)), "DD: bad ring", neow3j);
    }

    @Test
    void openRingUpTo_byAStranger_aborts() throws Throwable {
        assertAborted(send(deed, "openRingUpTo", stranger, integer(1)), "DD: not owner", neow3j);
    }

    @Test
    void setStyleCount_onlyRaisesTheCount() throws Throwable {
        SmartContract fresh = freshDeed();

        assertHalted(send(fresh, "setStyleCount", owner, integer(STYLES + 1)), neow3j);

        assertThat(read(fresh, "styleCount").getInteger()).isEqualTo(BigInteger.valueOf(STYLES + 1));
        assertAborted(send(fresh, "setStyleCount", owner, integer(STYLES)), "DD: bad styles", neow3j);
        assertAborted(send(fresh, "setStyleCount", owner, integer(33)), "DD: bad styles", neow3j);
    }

    @Test
    void setStyleCount_byAStranger_aborts() throws Throwable {
        assertAborted(send(deed, "setStyleCount", stranger, integer(STYLES + 1)), "DD: not owner", neow3j);
    }

    @Test
    void setTreasury_sendsLaterPaymentsElsewhere() throws Throwable {
        SmartContract fresh = freshDeed();
        Account next = Account.create();

        NeoApplicationLog.Execution execution = assertHalted(send(fresh, "setTreasury", owner, hash160(next)), neow3j);

        assertThat(read(fresh, "getTreasury").getByteArray()).isEqualTo(next.getScriptHash().toLittleEndianArray());
        assertThat(execution.getNotifications().get(0).getEventName()).isEqualTo("TreasuryChanged");
    }

    @Test
    void setTreasury_toTheContractItselfOrZero_aborts() throws Throwable {
        assertAborted(send(deed, "setTreasury", owner, hash160(deed.getScriptHash())), "DD: bad treasury", neow3j);
        assertAborted(send(deed, "setTreasury", owner, hash160(Hash160.ZERO)), "DD: bad treasury", neow3j);
    }

    @Test
    void setTreasury_byAStranger_aborts() throws Throwable {
        assertAborted(send(deed, "setTreasury", stranger, hash160(stranger)), "DD: not owner", neow3j);
    }

    @Test
    void setGate_replacesTheGate() throws Throwable {
        SmartContract fresh = freshDeed();
        ECKeyPair next = Account.create().getECKeyPair();

        assertHalted(send(fresh, "setGate", owner, publicKey(next.getPublicKey().getEncoded(true))), neow3j);

        assertThat(read(fresh, "getGate").getHexString()).isEqualTo(io.neow3j.utils.Numeric.toHexStringNoPrefix(next.getPublicKey().getEncoded(true)));
    }

    @Test
    void setGate_withAMalformedKey_aborts() throws Throwable {
        assertAborted(send(deed, "setGate", owner, byteArray(new byte[33])), "DD: bad gate", neow3j);
    }

    @Test
    void setGate_byAStranger_aborts() throws Throwable {
        assertAborted(send(deed, "setGate", stranger, publicKey(gate.getPublicKey().getEncoded(true))), "DD: not owner", neow3j);
    }

    @Test
    void setPhase_toFoundersWhileTheGuestbookIsOpen_aborts() throws Throwable {
        assertAborted(send(deed, "setPhase", owner, integer(1)), "DD: guestbook open", neow3j);

        assertThat(read(deed, "phase").getInteger()).isZero();
    }

    @Test
    void setPhase_skippingTheFoundersPhase_aborts() throws Throwable {
        assertAborted(send(deed, "setPhase", owner, integer(2)), "DD: bad phase", neow3j);
    }

    @Test
    void setPhase_byAStranger_aborts() throws Throwable {
        assertAborted(send(deed, "setPhase", stranger, integer(1)), "DD: not owner", neow3j);
    }

    @Test
    void claim_beforeTheCityOpens_aborts() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);

        assertAborted(send(deed, "claim", wallet, hash160(wallet), deedVoucher(neow3j, deed.getScriptHash(), wallet.getScriptHash(), gate)),
                "DD: not open", neow3j);
    }

    @Test
    void payingForANewcomerClaim_beforeTheCityOpens_abortsAndKeepsTheGas() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);
        BigInteger before = new io.neow3j.contract.GasToken(neow3j).getBalanceOf(wallet);

        assertAborted(pay(neow3j, deed, wallet, NEWCOMER_FEE, newcomer()), "DD: not open", neow3j);

        assertThat(new io.neow3j.contract.GasToken(neow3j).getBalanceOf(wallet)).isGreaterThan(before.subtract(NEWCOMER_FEE));
    }

    @Test
    void onNEP17Payment_calledByAnotherToken_aborts() throws Throwable {
        assertAborted(send(liar, "pretend", stranger, hash160(deed.getScriptHash()), hash160(stranger),
                integer(NEWCOMER_FEE), newcomer()), "DD: only GAS", neow3j);
    }

    @Test
    void transfer_alwaysAbortsBecauseDeedsAreSoulbound() throws Throwable {
        assertAborted(send(deed, "transfer", stranger, hash160(stranger), byteArray(new byte[]{1}), any(null)),
                "DD: soulbound", neow3j);
    }

    @Test
    void reads_ofAMalformedWallet_abort() throws Throwable {
        assertReadAborts(deed, "numberOf", "DD: bad wallet", byteArray(new byte[33]));
        assertReadAborts(deed, "balanceOf", "DD: bad wallet", hash160(Hash160.ZERO));
        assertReadAborts(deed, "tokensOf", "DD: bad wallet", byteArray(new byte[33]));
    }

    @Test
    void reads_ofAMissingToken_abortOrAreEmpty() throws Throwable {
        assertReadAborts(deed, "ownerOf", "DD: bad token", any(null));
        assertReadAborts(deed, "properties", "DD: no token", byteArray(new byte[]{1}));
        assertThat(read(deed, "deedAt", integer(1)).getValue()).isNull();
    }

    @Test
    void deeds_withABadPage_aborts() throws Throwable {
        assertReadAborts(deed, "deeds", "DD: bad page", integer(0), integer(1));
        assertReadAborts(deed, "deeds", "DD: bad page", integer(1), integer(0));
        assertReadAborts(deed, "deeds", "DD: bad page", integer(1), integer(65));
    }

    @Test
    void manifest_callsOnlyWhatItNeedsAndOffersNoDestroy() throws Throwable {
        ContractManifest manifest = deed.getManifest();

        assertThat(manifest.getSupportedStandards()).containsExactly("NEP-11");
        assertThat(manifest.getPermissions().stream().flatMap(p -> p.getMethods().stream()).collect(Collectors.toList()))
                .containsExactlyInAnyOrder("update", "verifyWithECDsa", "base64Decode", "itoa", "transfer", "numberOf", "isOpen");
        assertThat(manifest.getAbi().getMethods().stream().map(ContractManifest.ContractABI.ContractMethod::getName))
                .doesNotContain("destroy");
    }

    @Test
    void update_byAStranger_aborts() throws Throwable {
        assertAborted(sendUpdate(deed, UpdatedDeed.class, stranger), "DD: not owner", neow3j);
    }

    @Test
    void update_twiceInOneTransaction_aborts() throws Throwable {
        List<ContractParameter> update = updateParams(MempolisDeed.class, any(null));

        assertAborted(sendTwice(neow3j, deed, "update", update, update, AccountSigner.calledByEntry(owner)),
                "DD: re-entry", neow3j);
    }

    @Test
    void update_byTheOwner_swapsTheCodeAndKeepsTheStorage() throws Throwable {
        SmartContract fresh = freshDeed();
        assertHalted(send(fresh, "registerLots", owner, integer(1), byteArray(new byte[]{info(STANDARD, 0)})), neow3j);

        assertHalted(sendUpdate(fresh, UpdatedDeed.class, owner), neow3j);

        assertThat(read(fresh, "version").getInteger()).isEqualTo(BigInteger.TWO);
        assertThat(read(fresh, "lotCount").getInteger()).isEqualTo(BigInteger.ONE);
    }

    @Test
    void update_neverChangesTheOwnerWhateverDataItCarries() throws Throwable {
        SmartContract fresh = freshDeed();

        assertHalted(sendUpdate(fresh, MempolisDeed.class, owner,
                deployData(stranger.getScriptHash(), gate, guestbook.getScriptHash(), stranger.getScriptHash(), 1)), neow3j);

        assertThat(read(fresh, "getOwner").getByteArray()).isEqualTo(owner.getScriptHash().toLittleEndianArray());
        assertThat(read(fresh, "getTreasury").getByteArray()).isEqualTo(treasury.getScriptHash().toLittleEndianArray());
    }

    private static SmartContract freshDeed() throws Throwable {
        return deploy(MempolisDeed.class, fundedAccount(neow3j, ext),
                deployData(owner.getScriptHash(), gate, guestbook.getScriptHash(), treasury.getScriptHash(), STYLES), neow3j);
    }

    private static void assertDeployAborts(Hash160 owner, ECKeyPair gate, Hash160 book, Hash160 treasury, int styles,
            String message) throws Throwable {
        assertAborted(sendDeploy(MempolisDeed.class, fundedAccount(neow3j, ext), deployData(owner, gate, book, treasury, styles), neow3j),
                message, neow3j);
    }
}
