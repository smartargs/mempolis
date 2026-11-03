package com.smartargs.mempolis.deploy;

import com.smartargs.mempolis.deed.MempolisDeed;
import com.smartargs.mempolis.testkit.ReadyNeoExpressContainer;
import com.smartargs.mempolis.testkit.TestHelper;
import io.neow3j.contract.SmartContract;
import io.neow3j.crypto.ECKeyPair;
import io.neow3j.protocol.Neow3j;
import io.neow3j.test.ContractTest;
import io.neow3j.test.ContractTestExtension;
import io.neow3j.types.ContractParameter;
import io.neow3j.wallet.Account;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static com.smartargs.mempolis.testkit.TestHelper.assertHalted;
import static com.smartargs.mempolis.testkit.TestHelper.deploy;
import static com.smartargs.mempolis.testkit.TestHelper.fundedAccount;
import static com.smartargs.mempolis.testkit.TestHelper.read;
import static com.smartargs.mempolis.testkit.TestHelper.send;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.byteArray;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;
import static io.neow3j.types.ContractParameter.publicKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ContractTest(blockTime = 1, contracts = {}, configFile = TestHelper.NODE_CONFIG)
public class CitySetupTest {

    @RegisterExtension
    private static final ContractTestExtension ext = new ContractTestExtension(new ReadyNeoExpressContainer());

    private static final ECKeyPair GATE = Account.create().getECKeyPair();

    private static Neow3j neow3j;

    @BeforeAll
    static void setUp() {
        neow3j = ext.getNeow3j();
        neow3j.allowTransmissionOnFault();
    }

    @Test
    void apply_loadsMoreThanOneBatchOfLotsAndTheQueue() throws Throwable {
        Account owner = fundedAccount(neow3j, ext);
        SmartContract deed = freshDeed(owner);
        CityPlanFile plan = plan(450);

        new CitySetup(neow3j, deed.getScriptHash(), owner).apply(plan);

        assertThat(read(deed, "lotCount").getInteger()).isEqualTo(BigInteger.valueOf(450));
        assertThat(read(deed, "queueLength").getInteger()).isEqualTo(BigInteger.valueOf(plan.queue().size()));
        assertThat(read(deed, "tierOf", integer(450)).getInteger()).isEqualTo(BigInteger.valueOf(plan.lots().get(449) & 7));
        assertThat(new CitySetup(neow3j, deed.getScriptHash(), owner).missing(plan)).containsExactly(0, 0);
    }

    @Test
    void apply_afterAnInterruptedRun_sendsOnlyWhatIsMissing() throws Throwable {
        Account owner = fundedAccount(neow3j, ext);
        SmartContract deed = freshDeed(owner);
        CityPlanFile plan = plan(30);
        assertHalted(send(deed, "registerLots", owner, integer(1), byteArray(infos(plan.lots().subList(0, 10)))), neow3j);

        assertThat(new CitySetup(neow3j, deed.getScriptHash(), owner).missing(plan)).containsExactly(20, plan.queue().size());
        new CitySetup(neow3j, deed.getScriptHash(), owner).apply(plan);

        assertThat(read(deed, "lotCount").getInteger()).isEqualTo(BigInteger.valueOf(30));
    }

    @Test
    void missing_refusesAPlanThatDisagreesWithTheChain() throws Throwable {
        Account owner = fundedAccount(neow3j, ext);
        SmartContract deed = freshDeed(owner);
        CityPlanFile plan = plan(10);
        new CitySetup(neow3j, deed.getScriptHash(), owner).apply(plan);
        List<Integer> changed = new ArrayList<>(plan.lots());
        changed.set(4, changed.get(4) == 2 ? 3 : 2);

        assertThatThrownBy(() -> new CitySetup(neow3j, deed.getScriptHash(), owner).missing(new CityPlanFile(changed, plan.queue())))
                .hasMessageContaining("Lot 5");
    }

    private static CityPlanFile plan(int size) {
        List<Integer> lots = IntStream.rangeClosed(1, size).map(lot -> (lot % 4 == 0 ? 2 : 1) + 8 * (lot * 3 / size)).boxed().toList();
        List<Integer> queue = IntStream.rangeClosed(1, size).filter(lot -> (lots.get(lot - 1) & 7) == 1).boxed().toList();
        return new CityPlanFile(lots, queue);
    }

    private static byte[] infos(List<Integer> lots) {
        byte[] bytes = new byte[lots.size()];
        for (int i = 0; i < bytes.length; i++) bytes[i] = lots.get(i).byteValue();
        return bytes;
    }

    private static SmartContract freshDeed(Account owner) throws Throwable {
        Account placeholder = Account.create();
        ContractParameter data = array(hash160(owner), publicKey(GATE.getPublicKey().getEncoded(true)),
                hash160(placeholder), hash160(placeholder), integer(4));
        return deploy(MempolisDeed.class, fundedAccount(neow3j, ext), data, neow3j);
    }
}
