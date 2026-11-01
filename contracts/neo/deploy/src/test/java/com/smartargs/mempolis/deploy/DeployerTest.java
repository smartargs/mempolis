package com.smartargs.mempolis.deploy;

import com.smartargs.mempolis.guestbook.FoundersGuestbook;
import com.smartargs.mempolis.testkit.ReadyNeoExpressContainer;
import com.smartargs.mempolis.testkit.TestHelper;
import io.neow3j.compiler.CompilationUnit;
import io.neow3j.compiler.Compiler;
import io.neow3j.contract.SmartContract;
import io.neow3j.crypto.ECKeyPair;
import io.neow3j.crypto.ECKeyPair.ECPublicKey;
import io.neow3j.protocol.Neow3j;
import io.neow3j.test.ContractTest;
import io.neow3j.test.ContractTestExtension;
import io.neow3j.test.DeployConfig;
import io.neow3j.test.DeployConfiguration;
import io.neow3j.test.DeployContext;
import io.neow3j.types.Hash160;
import io.neow3j.types.Hash256;
import io.neow3j.wallet.Account;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Optional;

import static com.smartargs.mempolis.testkit.TestHelper.fundedAccount;
import static com.smartargs.mempolis.testkit.TestHelper.ownerAndGate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ContractTest(blockTime = 1, contracts = FoundersGuestbook.class, configFile = TestHelper.NODE_CONFIG)
public class DeployerTest {

    @RegisterExtension
    private static final ContractTestExtension ext = new ContractTestExtension(new ReadyNeoExpressContainer());

    private static final Hash160 OWNER = Account.create().getScriptHash();
    private static final ECKeyPair GATE = Account.create().getECKeyPair();
    private static final Optional<ECPublicKey> KEY = Optional.of(GATE.getPublicKey());

    private static Neow3j neow3j;
    private static ContractArtifact artifact;

    @DeployConfig(FoundersGuestbook.class)
    public static DeployConfiguration configure(DeployContext ctx) {
        DeployConfiguration config = new DeployConfiguration();
        config.setDeployParam(ownerAndGate(OWNER, GATE));
        return config;
    }

    @BeforeAll
    static void setUp() throws Throwable {
        neow3j = ext.getNeow3j();
        CompilationUnit unit = new Compiler().compile(FoundersGuestbook.class.getCanonicalName());
        artifact = new ContractArtifact(unit.getNefFile(), unit.getManifest());
    }

    private static Deployer deployer() throws Throwable {
        return new Deployer(neow3j, fundedAccount(neow3j, ext));
    }

    private static void deploy(Deployer deployer, DeploymentPlan plan) throws Throwable {
        Hash256 transaction = deployer.send(plan);
        deployer.confirm(plan, transaction);
    }

    @Test
    void aPlanSendsNothingAndNamesItsCost() throws Throwable {
        DeploymentPlan plan = deployer().plan(artifact, OWNER, KEY);

        assertThat(plan.alreadyDeployed()).isFalse();
        assertThat(plan.fee()).isPositive();
        assertThat(neow3j.getContractState(plan.hash()).send().hasError()).isTrue();
    }

    @Test
    void deployingPutsTheContractOnChainWithTheNamedOwner() throws Throwable {
        Deployer deployer = deployer();
        DeploymentPlan plan = deployer.plan(artifact, OWNER, KEY);

        deploy(deployer, plan);

        SmartContract contract = new SmartContract(plan.hash(), neow3j);
        assertThat(contract.getName()).isEqualTo(artifact.name());
        assertThat(contract.callFunctionReturningScriptHash("getOwner")).isEqualTo(OWNER);
    }

    @Test
    void theOwnerDoesNotHaveToBeTheDeployer() throws Throwable {
        Deployer deployer = deployer();
        Hash160 multiSig = Account.create().getScriptHash();
        DeploymentPlan plan = deployer.plan(artifact, multiSig, KEY);

        deploy(deployer, plan);

        assertThat(new SmartContract(plan.hash(), neow3j).callFunctionReturningScriptHash("getOwner"))
                .isEqualTo(multiSig);
    }

    @Test
    void planningADeployedContractAgainSendsNothing() throws Throwable {
        Deployer deployer = deployer();
        DeploymentPlan first = deployer.plan(artifact, OWNER, KEY);
        deploy(deployer, first);

        DeploymentPlan second = deployer.plan(artifact, OWNER, KEY);

        assertThat(second.alreadyDeployed()).isTrue();
        assertThat(second.hash()).isEqualTo(first.hash());
        assertThat(second.fee()).isZero();
    }

    @Test
    void aDeployedContractWithAnotherOwnerIsRefused() throws Throwable {
        Deployer deployer = deployer();
        deploy(deployer, deployer.plan(artifact, OWNER, KEY));

        assertThatThrownBy(() -> deployer.plan(artifact, Account.create().getScriptHash(), KEY))
                .hasMessageContaining("is owned by");
    }

    @Test
    void anOwnerTheContractRejectsIsRefusedBeforeSending() throws Throwable {
        Deployer deployer = deployer();

        assertThatThrownBy(() -> deployer.plan(artifact, Hash160.ZERO, KEY)).hasMessageContaining("GB: bad owner");
    }

    @Test
    void sendingAPlanForADeployedContractIsRefused() throws Throwable {
        Deployer deployer = deployer();
        deploy(deployer, deployer.plan(artifact, OWNER, KEY));
        DeploymentPlan nothingToSend = deployer.plan(artifact, OWNER, KEY);

        assertThatThrownBy(() -> deployer.send(nothingToSend)).hasMessageContaining("already deployed");
    }

    @Test
    void theDeployedContractHoldsTheNamedGate() throws Throwable {
        Deployer deployer = deployer();
        DeploymentPlan plan = deployer.plan(artifact, OWNER, KEY);

        deploy(deployer, plan);

        byte[] gate = new SmartContract(plan.hash(), neow3j).callInvokeFunction("getGate").getInvocationResult().getStack().get(0).getByteArray();
        assertThat(gate).isEqualTo(GATE.getPublicKey().getEncoded(true));
    }

    @Test
    void aDeployedContractWithAnotherGateIsRefused() throws Throwable {
        Deployer deployer = deployer();
        deploy(deployer, deployer.plan(artifact, OWNER, KEY));
        Optional<ECPublicKey> other = Optional.of(Account.create().getECKeyPair().getPublicKey());

        assertThatThrownBy(() -> deployer.plan(artifact, OWNER, other)).hasMessageContaining("different gate");
    }

    @Test
    void theGuestbookRefusesToDeployWithoutAGate() throws Throwable {
        Deployer deployer = deployer();

        assertThatThrownBy(() -> deployer.plan(artifact, OWNER, Optional.empty())).hasMessageContaining("GB: bad owner");
    }
}
