package com.smartargs.mempolis.guestbook;

import com.smartargs.mempolis.guestbook.testsupport.UpdatedGuestbook;
import com.smartargs.mempolis.testkit.ReadyNeoExpressContainer;
import com.smartargs.mempolis.testkit.TestHelper;
import io.neow3j.contract.SmartContract;
import io.neow3j.crypto.ECKeyPair;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.NeoApplicationLog;
import io.neow3j.protocol.core.response.Notification;
import io.neow3j.test.ContractTest;
import io.neow3j.test.ContractTestExtension;
import io.neow3j.test.DeployConfig;
import io.neow3j.test.DeployConfiguration;
import io.neow3j.test.DeployContext;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;
import io.neow3j.wallet.Account;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.smartargs.mempolis.testkit.TestHelper.assertAborted;
import static com.smartargs.mempolis.testkit.TestHelper.assertFaulted;
import static com.smartargs.mempolis.testkit.TestHelper.assertHalted;
import static com.smartargs.mempolis.testkit.TestHelper.deploy;
import static com.smartargs.mempolis.testkit.TestHelper.fundedAccount;
import static com.smartargs.mempolis.testkit.TestHelper.ownerAndGate;
import static com.smartargs.mempolis.testkit.TestHelper.read;
import static com.smartargs.mempolis.testkit.TestHelper.send;
import static com.smartargs.mempolis.testkit.TestHelper.sendDeploy;
import static com.smartargs.mempolis.testkit.TestHelper.sendUpdate;
import static com.smartargs.mempolis.testkit.TestHelper.voucher;
import static io.neow3j.types.ContractParameter.any;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.byteArray;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.publicKey;
import static io.neow3j.types.ContractParameter.string;
import static org.assertj.core.api.Assertions.assertThat;

@ContractTest(blockTime = 1, contracts = FoundersGuestbook.class, configFile = TestHelper.NODE_CONFIG)
public class GuestbookVoucherTest {

    @RegisterExtension
    private static final ContractTestExtension ext = new ContractTestExtension(new ReadyNeoExpressContainer());

    private static final Account owner = Account.create();
    private static final ECKeyPair gate = Account.create().getECKeyPair();

    private static Neow3j neow3j;
    private static SmartContract guestbook;
    private static Account stranger;

    @DeployConfig(FoundersGuestbook.class)
    public static DeployConfiguration configure(DeployContext ctx) {
        DeployConfiguration config = new DeployConfiguration();
        config.setDeployParam(ownerAndGate(owner.getScriptHash(), gate));
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

    private static ContractParameter voucherFor(Account wallet) {
        return voucher(guestbook.getScriptHash(), wallet.getScriptHash(), gate);
    }

    private static byte[] gateKey(ECKeyPair key) {
        return key.getPublicKey().getEncoded(true);
    }

    @Test
    void deploy_storesTheGate() throws Throwable {
        assertThat(read(guestbook, "getGate").getByteArray()).isEqualTo(gateKey(gate));
    }

    @Test
    void deploy_withoutAGate_aborts() throws Throwable {
        ContractParameter data = array(hash160(owner.getScriptHash()), any(null));

        assertAborted(sendDeploy(FoundersGuestbook.class, stranger, data, neow3j), "GB: bad gate", neow3j);
    }

    @Test
    void deploy_withAMalformedGate_aborts() throws Throwable {
        ContractParameter data = array(hash160(owner.getScriptHash()), byteArray(new byte[20]));

        assertAborted(sendDeploy(FoundersGuestbook.class, stranger, data, neow3j), "GB: bad gate", neow3j);
    }

    @Test
    void sign_withTheGatesVoucher_works() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);

        assertHalted(send(guestbook, "sign", wallet, hash160(wallet), voucherFor(wallet)), neow3j);

        assertThat(read(guestbook, "numberOf", hash160(wallet)).getInteger().signum()).isPositive();
    }

    @Test
    void sign_withoutAVoucher_aborts() throws Throwable {
        assertAborted(send(guestbook, "sign", stranger, hash160(stranger), any(null)), "GB: bad voucher", neow3j);
    }

    @Test
    void sign_withAVoucherFromAnotherKey_aborts() throws Throwable {
        ContractParameter forged = voucher(guestbook.getScriptHash(), stranger.getScriptHash(), Account.create().getECKeyPair());

        assertAborted(send(guestbook, "sign", stranger, hash160(stranger), forged), "GB: bad voucher", neow3j);
    }

    @Test
    void sign_withAnotherWalletsVoucher_aborts() throws Throwable {
        ContractParameter theirs = voucherFor(fundedAccount(neow3j, ext));

        assertAborted(send(guestbook, "sign", stranger, hash160(stranger), theirs), "GB: bad voucher", neow3j);
    }

    @Test
    void sign_withAVoucherForAnotherContract_aborts() throws Throwable {
        ContractParameter elsewhere = voucher(Hash160.ZERO, stranger.getScriptHash(), gate);

        assertAborted(send(guestbook, "sign", stranger, hash160(stranger), elsewhere), "GB: bad voucher", neow3j);
    }

    @Test
    void sign_withATamperedVoucher_aborts() throws Throwable {
        String good = voucherFor(stranger).getValue().toString(), flipped = (good.charAt(0) == 'A' ? 'B' : 'A') + good.substring(1);

        assertAborted(send(guestbook, "sign", stranger, hash160(stranger), string(flipped)), "GB: bad voucher", neow3j);
    }

    @Test
    void sign_withAVoucherOfTheWrongLength_aborts() throws Throwable {
        assertAborted(send(guestbook, "sign", stranger, hash160(stranger), string("c2hvcnQ=")), "GB: bad voucher", neow3j);
    }

    @Test
    void sign_withAVoucherThatIsNotBase64_faults() throws Throwable {
        assertFaulted(send(guestbook, "sign", stranger, hash160(stranger), string("!".repeat(88))), neow3j);
        assertThat(read(guestbook, "numberOf", hash160(stranger)).getInteger().signum()).isZero();
    }

    @Test
    void sign_cannotUseAValidVoucherOnBehalfOfAnotherWallet() throws Throwable {
        Account victim = fundedAccount(neow3j, ext);

        assertAborted(send(guestbook, "sign", stranger, hash160(victim), voucherFor(victim)), "GB: no witness", neow3j);
    }

    @Test
    void sign_cannotReuseAVoucher() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);
        ContractParameter once = voucherFor(wallet);
        assertHalted(send(guestbook, "sign", wallet, hash160(wallet), once), neow3j);

        assertAborted(send(guestbook, "sign", wallet, hash160(wallet), once), "GB: already signed", neow3j);
    }

    @Test
    void setGate_byAStranger_aborts() throws Throwable {
        assertAborted(send(guestbook, "setGate", stranger, publicKey(gateKey(Account.create().getECKeyPair()))), "GB: not owner", neow3j);
    }

    @Test
    void setGate_withoutAKey_aborts() throws Throwable {
        assertAborted(send(guestbook, "setGate", owner, any(null)), "GB: bad gate", neow3j);
    }

    @Test
    void setGate_swapsTheKeySoOldVouchersStopWorking() throws Throwable {
        Account instanceOwner = fundedAccount(neow3j, ext), early = fundedAccount(neow3j, ext), late = fundedAccount(neow3j, ext);
        SmartContract instance = deploy(FoundersGuestbook.class, fundedAccount(neow3j, ext), ownerAndGate(instanceOwner.getScriptHash(), gate), neow3j);
        ECKeyPair replacement = Account.create().getECKeyPair();
        assertHalted(send(instance, "sign", early, hash160(early), voucher(instance.getScriptHash(), early.getScriptHash(), gate)), neow3j);

        NeoApplicationLog.Execution swapped = assertHalted(send(instance, "setGate", instanceOwner, publicKey(gateKey(replacement))), neow3j);

        Notification changed = swapped.getNotifications().get(0);
        assertThat(changed.getEventName()).isEqualTo("GateChanged");
        assertThat(read(instance, "getGate").getByteArray()).isEqualTo(gateKey(replacement));
        assertAborted(send(instance, "sign", late, hash160(late), voucher(instance.getScriptHash(), late.getScriptHash(), gate)), "GB: bad voucher", neow3j);
        assertHalted(send(instance, "sign", late, hash160(late), voucher(instance.getScriptHash(), late.getScriptHash(), replacement)), neow3j);
        assertThat(read(instance, "numberOf", hash160(early)).getInteger().intValue()).isEqualTo(1);
    }

    @Test
    void theGateKeyAloneCannotSignForAWallet() throws Throwable {
        Account wallet = fundedAccount(neow3j, ext);
        Account gateAccount = new Account(gate);
        TestHelper.fund(neow3j, ext, gateAccount.getScriptHash());

        assertAborted(send(guestbook, "sign", gateAccount, hash160(wallet), voucherFor(wallet)), "GB: no witness", neow3j);
    }

    @Test
    void theGateKeyCannotActAsTheOwner() throws Throwable {
        Account gateAccount = new Account(gate);
        TestHelper.fund(neow3j, ext, gateAccount.getScriptHash());

        assertAborted(send(guestbook, "close", gateAccount), "GB: not owner", neow3j);
        assertAborted(send(guestbook, "setGate", gateAccount, publicKey(gateKey(Account.create().getECKeyPair()))), "GB: not owner", neow3j);
        assertAborted(sendUpdate(guestbook, UpdatedGuestbook.class, gateAccount), "GB: not owner", neow3j);
    }
}
