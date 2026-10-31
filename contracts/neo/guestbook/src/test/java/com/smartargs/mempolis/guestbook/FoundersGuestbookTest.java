package com.smartargs.mempolis.guestbook;

import com.smartargs.mempolis.guestbook.testsupport.UpdatedGuestbook;
import com.smartargs.mempolis.testkit.ReadyNeoExpressContainer;
import com.smartargs.mempolis.testkit.TestHelper;
import io.neow3j.contract.GasToken;
import io.neow3j.contract.SmartContract;
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
import static com.smartargs.mempolis.testkit.TestHelper.fundedAccount;
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

@ContractTest(blockTime = 1, contracts = FoundersGuestbook.class, configFile = TestHelper.NODE_CONFIG)
public class FoundersGuestbookTest {

    @RegisterExtension
    private static final ContractTestExtension ext = new ContractTestExtension(new ReadyNeoExpressContainer());

    private static final Account owner = Account.create();

    private static Neow3j neow3j;
    private static SmartContract guestbook;
    private static Account stranger;

    @DeployConfig(FoundersGuestbook.class)
    public static DeployConfiguration configure(DeployContext ctx) {
        DeployConfiguration config = new DeployConfiguration();
        config.setDeployParam(hash160(owner.getScriptHash()));
        return config;
    }

    @BeforeAll
    static void setUp() throws Throwable {
        neow3j = ext.getNeow3j();
        neow3j.allowTransmissionOnFault();
        guestbook = ext.getDeployedContract(FoundersGuestbook.class);
        TestHelper.fund(neow3j, ext, owner.getScriptHash());
        stranger = fundedAccount(neow3j, ext);
    }

    @Test
    void deploy_storesTheOwnerAndOpensTheBook() throws Throwable {
        assertThat(read(guestbook, "getOwner").getByteArray()).isEqualTo(owner.getScriptHash().toLittleEndianArray());
        assertThat(read(guestbook, "isOpen").getBoolean()).isTrue();
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
    void sign_recordsTheNextNumberInBothDirections() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);
        BigInteger before = read(guestbook, "getCount").getInteger();

        Hash256 tx = send(guestbook, "sign", wallet, hash160(wallet));
        NeoApplicationLog.Execution execution = assertHalted(tx, neow3j);

        BigInteger number = execution.getStack().get(0).getInteger();
        assertThat(read(guestbook, "getCount").getInteger()).isEqualTo(number);
        assertThat(number).isGreaterThan(before);
        assertThat(read(guestbook, "numberOf", hash160(wallet)).getInteger()).isEqualTo(number);
        assertThat(read(guestbook, "founderAt", integer(number)).getByteArray())
                .isEqualTo(wallet.getScriptHash().toLittleEndianArray());
    }

    @Test
    void sign_firesSignedWithTheWalletAndNumber() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);

        NeoApplicationLog.Execution execution = assertHalted(send(guestbook, "sign", wallet, hash160(wallet)), neow3j);

        Notification signed = execution.getNotifications().get(0);
        assertThat(signed.getEventName()).isEqualTo("Signed");
        assertThat(signed.getContract()).isEqualTo(guestbook.getScriptHash());
        assertThat(signed.getState().getList().get(0).getByteArray())
                .isEqualTo(wallet.getScriptHash().toLittleEndianArray());
        assertThat(signed.getState().getList().get(1).getInteger()).isEqualTo(execution.getStack().get(0).getInteger());
    }

    @Test
    void sign_givesConsecutiveWalletsConsecutiveNumbers() throws Throwable {
        Account first = fundedAccount(neow3j, ext);
        Account second = fundedAccount(neow3j, ext);

        BigInteger a = assertHalted(send(guestbook, "sign", first, hash160(first)), neow3j)
                .getStack().get(0).getInteger();
        BigInteger b = assertHalted(send(guestbook, "sign", second, hash160(second)), neow3j)
                .getStack().get(0).getInteger();

        assertThat(b).isGreaterThan(a);
        assertThat(read(guestbook, "numberOf", hash160(first)).getInteger()).isEqualTo(a);
    }

    @Test
    void sign_aSecondTime_abortsAndKeepsTheFirstNumber() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);
        BigInteger number = assertHalted(send(guestbook, "sign", wallet, hash160(wallet)), neow3j)
                .getStack().get(0).getInteger();

        assertAborted(send(guestbook, "sign", wallet, hash160(wallet)), "GB: already signed", neow3j);

        assertThat(read(guestbook, "numberOf", hash160(wallet)).getInteger()).isEqualTo(number);
    }

    @Test
    void sign_forAnotherWallet_aborts() throws Throwable {
        Account victim = Account.create();

        assertAborted(send(guestbook, "sign", stranger, hash160(victim)), "GB: no witness", neow3j);

        assertThat(read(guestbook, "numberOf", hash160(victim)).getInteger()).isZero();
    }

    @Test
    void sign_withAPublicKeyInsteadOfAHash_aborts() throws Throwable {
        ContractParameter publicKey = byteArray(stranger.getECKeyPair().getPublicKey().getEncoded(true));

        assertAborted(send(guestbook, "sign", stranger, publicKey), "GB: bad wallet", neow3j);
    }

    @Test
    void sign_withTheZeroHash_aborts() throws Throwable {
        assertAborted(send(guestbook, "sign", stranger, hash160(Hash160.ZERO)), "GB: bad wallet", neow3j);
    }

    @Test
    void sign_withoutAWallet_aborts() throws Throwable {
        assertAborted(send(guestbook, "sign", stranger, any(null)), "GB: bad wallet", neow3j);
    }

    @Test
    void sign_twiceInOneTransaction_aborts() throws Throwable {
        Account first = fundedAccount(neow3j, ext);
        Account second = fundedAccount(neow3j, ext);

        Hash256 tx = sendTwice(neow3j, guestbook, "sign", List.of(hash160(first)), List.of(hash160(second)),
                AccountSigner.calledByEntry(first), AccountSigner.calledByEntry(second));

        assertAborted(tx, "GB: re-entry", neow3j);
        assertThat(read(guestbook, "numberOf", hash160(first)).getInteger()).isZero();
    }

    @Test
    void close_byAStranger_abortsAndLeavesTheBookOpen() throws Throwable {
        assertAborted(send(guestbook, "close", stranger), "GB: not owner", neow3j);

        assertThat(read(guestbook, "isOpen").getBoolean()).isTrue();
    }

    @Test
    void close_twiceInOneTransaction_abortsAndLeavesTheBookOpen() throws Throwable {
        assertAborted(sendTwice(neow3j, guestbook, "close", List.of(), List.of(), AccountSigner.calledByEntry(owner)),
                "GB: re-entry", neow3j);

        assertThat(read(guestbook, "isOpen").getBoolean()).isTrue();
    }

    @Test
    void numberOf_aWalletThatNeverSigned_isZero() throws Throwable {
        assertThat(read(guestbook, "numberOf", hash160(Account.create())).getInteger()).isZero();
    }

    @Test
    void numberOf_aMalformedWallet_aborts() throws Throwable {
        assertReadAborts(guestbook, "numberOf", "GB: bad wallet", byteArray(new byte[33]));
    }

    @Test
    void founderAt_aNumberNoWalletHolds_isNull() throws Throwable {
        assertThat(read(guestbook, "founderAt", integer(0)).getType()).isEqualTo(StackItemType.ANY);
        assertThat(read(guestbook, "founderAt", integer(1_000_000)).getType()).isEqualTo(StackItemType.ANY);
    }

    @Test
    void sendingGas_faultsSoNoFundsCanEverStrand() throws Throwable {
        BigInteger before = new GasToken(neow3j).getBalanceOf(guestbook.getScriptHash());

        Hash256 tx = new GasToken(neow3j).transfer(stranger, guestbook.getScriptHash(), BigInteger.ONE)
                .sign().send().getSendRawTransaction().getHash();

        assertFaulted(tx, neow3j);
        assertThat(new GasToken(neow3j).getBalanceOf(guestbook.getScriptHash())).isEqualTo(before);
    }

    @Test
    void manifest_canOnlyCallUpdateAndOffersNoDestroy() throws Throwable {
        ContractManifest manifest = guestbook.getManifest();

        assertThat(manifest.getPermissions()).hasSize(1);
        assertThat(manifest.getPermissions().get(0).getMethods()).containsExactly("update");
        assertThat(manifest.getAbi().getMethods().stream()
                .map(ContractManifest.ContractABI.ContractMethod::getName).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("_deploy", "sign", "close", "update", "isOpen", "getOwner", "getCount",
                        "numberOf", "founderAt");
    }

    @Test
    void update_byAStranger_aborts() throws Throwable {
        assertAborted(sendUpdate(guestbook, UpdatedGuestbook.class, stranger), "GB: not owner", neow3j);
    }

    @Test
    void update_twiceInOneTransaction_aborts() throws Throwable {
        List<ContractParameter> update = updateParams(FoundersGuestbook.class, any(null));

        assertAborted(sendTwice(neow3j, guestbook, "update", update, update, AccountSigner.calledByEntry(owner)),
                "GB: re-entry", neow3j);
    }

    @Test
    void update_byTheOwner_swapsTheCodeAndKeepsTheStorage() throws Throwable {
        Account instanceOwner = fundedAccount(neow3j, ext);
        SmartContract instance = deploy(FoundersGuestbook.class, fundedAccount(neow3j, ext), hash160(instanceOwner),
                neow3j);
        assertHalted(send(instance, "sign", instanceOwner, hash160(instanceOwner)), neow3j);

        assertHalted(sendUpdate(instance, UpdatedGuestbook.class, instanceOwner), neow3j);

        assertThat(read(instance, "version").getInteger()).isEqualTo(BigInteger.TWO);
        assertThat(read(instance, "getCount").getInteger()).isEqualTo(BigInteger.ONE);
    }

    @Test
    void update_neverChangesTheOwnerWhateverDataItCarries() throws Throwable {
        Account instanceOwner = fundedAccount(neow3j, ext);
        SmartContract instance = deploy(FoundersGuestbook.class, fundedAccount(neow3j, ext), hash160(instanceOwner),
                neow3j);

        assertHalted(sendUpdate(instance, FoundersGuestbook.class, instanceOwner, hash160(stranger)), neow3j);

        assertThat(read(instance, "getOwner").getByteArray())
                .isEqualTo(instanceOwner.getScriptHash().toLittleEndianArray());
    }

    private static void assertDeployAborts(ContractParameter data) throws Throwable {
        assertAborted(sendDeploy(FoundersGuestbook.class, stranger, data, neow3j), "GB: bad owner", neow3j);
    }
}
