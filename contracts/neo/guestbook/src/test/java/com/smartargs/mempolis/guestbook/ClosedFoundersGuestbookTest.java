package com.smartargs.mempolis.guestbook;

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
import io.neow3j.wallet.Account;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.math.BigInteger;

import static com.smartargs.mempolis.testkit.TestHelper.assertAborted;
import static com.smartargs.mempolis.testkit.TestHelper.assertHalted;
import static com.smartargs.mempolis.testkit.TestHelper.fundedAccount;
import static com.smartargs.mempolis.testkit.TestHelper.read;
import static com.smartargs.mempolis.testkit.TestHelper.ownerAndGate;
import static com.smartargs.mempolis.testkit.TestHelper.send;
import static com.smartargs.mempolis.testkit.TestHelper.voucher;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;
import static org.assertj.core.api.Assertions.assertThat;

@ContractTest(blockTime = 1, contracts = FoundersGuestbook.class, configFile = TestHelper.NODE_CONFIG)
public class ClosedFoundersGuestbookTest {

    @RegisterExtension
    private static final ContractTestExtension ext = new ContractTestExtension(new ReadyNeoExpressContainer());

    private static final Account owner = Account.create();
    private static final ECKeyPair gate = Account.create().getECKeyPair();

    private static Neow3j neow3j;
    private static SmartContract guestbook;
    private static Account founder;
    private static NeoApplicationLog.Execution closing;

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
        founder = fundedAccount(neow3j, ext);
        assertHalted(send(guestbook, "sign", founder, hash160(founder), voucher(guestbook.getScriptHash(), founder.getScriptHash(), gate)), neow3j);
        closing = assertHalted(send(guestbook, "close", owner), neow3j);
    }

    @Test
    void close_firesClosedWithTheFounderCount() {
        Notification closed = closing.getNotifications().get(0);

        assertThat(closed.getEventName()).isEqualTo("Closed");
        assertThat(closed.getState().getList().get(0).getInteger()).isEqualTo(BigInteger.ONE);
    }

    @Test
    void isOpen_isFalseOnceClosed() throws Throwable {
        assertThat(read(guestbook, "isOpen").getBoolean()).isFalse();
    }

    @Test
    void sign_afterClosing_aborts() throws Throwable {
        Account latecomer = fundedAccount(neow3j, ext);

        assertAborted(send(guestbook, "sign", latecomer, hash160(latecomer), voucher(guestbook.getScriptHash(), latecomer.getScriptHash(), gate)), "GB: closed", neow3j);

        assertThat(read(guestbook, "getCount").getInteger()).isEqualTo(BigInteger.ONE);
    }

    @Test
    void close_aSecondTime_aborts() throws Throwable {
        assertAborted(send(guestbook, "close", owner), "GB: already closed", neow3j);
    }

    @Test
    void founders_stayReadableAfterClosing() throws Throwable {
        assertThat(read(guestbook, "numberOf", hash160(founder)).getInteger()).isEqualTo(BigInteger.ONE);
        assertThat(read(guestbook, "founderAt", integer(1)).getByteArray())
                .isEqualTo(founder.getScriptHash().toLittleEndianArray());
    }
}
