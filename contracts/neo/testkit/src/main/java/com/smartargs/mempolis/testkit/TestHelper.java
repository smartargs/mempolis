package com.smartargs.mempolis.testkit;

import io.neow3j.compiler.CompilationUnit;
import io.neow3j.compiler.Compiler;
import io.neow3j.contract.ContractManagement;
import io.neow3j.contract.GasToken;
import io.neow3j.contract.SmartContract;
import io.neow3j.crypto.ECKeyPair;
import io.neow3j.crypto.Sign;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.ObjectMapperFactory;
import io.neow3j.protocol.core.response.InvocationResult;
import io.neow3j.protocol.core.response.NeoApplicationLog;
import io.neow3j.protocol.core.stackitem.StackItem;
import io.neow3j.script.ScriptBuilder;
import io.neow3j.test.ContractTestExtension;
import io.neow3j.transaction.AccountSigner;
import io.neow3j.transaction.Signer;
import io.neow3j.transaction.Transaction;
import io.neow3j.transaction.TransactionBuilder;
import io.neow3j.types.CallFlags;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;
import io.neow3j.types.Hash256;
import io.neow3j.types.NeoVMStateType;
import io.neow3j.utils.ArrayUtils;
import io.neow3j.utils.Await;
import io.neow3j.wallet.Account;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Base64;
import java.util.List;

import static io.neow3j.types.ContractParameter.any;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.byteArray;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.publicKey;
import static io.neow3j.types.ContractParameter.string;
import static org.assertj.core.api.Assertions.assertThat;

/** Chain setup and assertions shared by the contract tests. */
public final class TestHelper {

    /** Node config for {@code @ContractTest}: the default one with a gas limit high enough to deploy on Neo 3.10. */
    public static final String NODE_CONFIG = "test.neo-express";

    private static final BigInteger GAS_FUNDING = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(8));

    private TestHelper() {}

    /** A new account funded with enough GAS to pay for test transactions. */
    public static Account fundedAccount(Neow3j neow3j, ContractTestExtension ext) throws Throwable {
        Account account = Account.create();
        fund(neow3j, ext, account.getScriptHash());
        return account;
    }

    /** Funds an existing account from the genesis multi-sig. */
    public static void fund(Neow3j neow3j, ContractTestExtension ext, Hash160 to) throws Throwable {
        ContractTestExtension.GenesisAccount genesis = ext.getGenesisAccount();
        Account multiSig = genesis.getMultiSigAccount();
        TransactionBuilder builder = new GasToken(neow3j).transfer(multiSig, to, GAS_FUNDING)
                .signers(AccountSigner.calledByEntry(multiSig));
        Transaction tx = builder.getUnsignedTransaction()
                .addMultiSigWitness(multiSig.getVerificationScript(), genesis.getSignerAccounts());
        Await.waitUntilTransactionIsExecuted(tx.send().getSendRawTransaction().getHash(), neow3j);
    }

    /** Sends one contract invocation signed by {@code signer}, without waiting for it. */
    public static Hash256 send(SmartContract contract, String method, Account signer, ContractParameter... params)
            throws Throwable {
        return contract.invokeFunction(method, params)
                .signers(AccountSigner.calledByEntry(signer))
                .sign().send().getSendRawTransaction().getHash();
    }

    /** Sends the same call twice in one transaction, the way a batching script would. */
    public static Hash256 sendTwice(Neow3j neow3j, SmartContract contract, String method,
            List<ContractParameter> first, List<ContractParameter> second, Signer... signers) throws Throwable {
        byte[] script = new ScriptBuilder()
                .contractCall(contract.getScriptHash(), method, first, CallFlags.ALL)
                .contractCall(contract.getScriptHash(), method, second, CallFlags.ALL)
                .toArray();
        return new TransactionBuilder(neow3j).script(script).signers(signers)
                .sign().send().getSendRawTransaction().getHash();
    }

    /** Sends a deployment of {@code contract}, without waiting for it. A sender can deploy each contract once. */
    public static Hash256 sendDeploy(Class<?> contract, Account sender, ContractParameter data, Neow3j neow3j)
            throws Throwable {
        CompilationUnit unit = new Compiler().compile(contract.getCanonicalName());
        return new ContractManagement(neow3j).deploy(unit.getNefFile(), unit.getManifest(), data)
                .signers(AccountSigner.calledByEntry(sender))
                .sign().send().getSendRawTransaction().getHash();
    }

    /** Deploys {@code contract} from {@code sender} and returns it, for tests that need a fresh instance. */
    public static SmartContract deploy(Class<?> contract, Account sender, ContractParameter data, Neow3j neow3j)
            throws Throwable {
        assertHalted(sendDeploy(contract, sender, data, neow3j), neow3j);
        CompilationUnit unit = new Compiler().compile(contract.getCanonicalName());
        Hash160 hash = SmartContract.calcContractHash(sender.getScriptHash(), unit.getNefFile().getCheckSumAsInteger(),
                unit.getManifest().getName());
        return new SmartContract(hash, neow3j);
    }

    /** Asks {@code contract} to update itself to the code of {@code code}, signed by {@code signer}. */
    public static Hash256 sendUpdate(SmartContract contract, Class<?> code, Account signer) throws Throwable {
        return sendUpdate(contract, code, signer, any(null));
    }

    /** Asks {@code contract} to update itself, handing {@code data} to the deploy callback. */
    public static Hash256 sendUpdate(SmartContract contract, Class<?> code, Account signer, ContractParameter data)
            throws Throwable {
        List<ContractParameter> params = updateParams(code, data);
        return send(contract, "update", signer, params.toArray(new ContractParameter[0]));
    }

    /** The parameters of an {@code update} call to the code of {@code code}. */
    public static List<ContractParameter> updateParams(Class<?> code, ContractParameter data) throws Throwable {
        CompilationUnit unit = new Compiler().compile(code.getCanonicalName());
        String manifest = ObjectMapperFactory.getObjectMapper().writeValueAsString(unit.getManifest());
        return List.of(byteArray(unit.getNefFile().toArray()), string(manifest), data);
    }

    /** The execution of a transaction that must have succeeded. */
    public static NeoApplicationLog.Execution assertHalted(Hash256 tx, Neow3j neow3j) throws IOException {
        NeoApplicationLog.Execution execution = executionOf(tx, neow3j);
        assertThat(execution.getState()).as(execution.getException()).isEqualTo(NeoVMStateType.HALT);
        return execution;
    }

    /** Asserts that a transaction faulted with {@code message}. Needs {@code allowTransmissionOnFault}. */
    public static void assertAborted(Hash256 tx, String message, Neow3j neow3j) throws IOException {
        NeoApplicationLog.Execution execution = executionOf(tx, neow3j);
        assertThat(execution.getState()).isEqualTo(NeoVMStateType.FAULT);
        assertThat(execution.getException()).contains(message);
    }

    /** Asserts that a transaction faulted, whatever the reason. Needs {@code allowTransmissionOnFault}. */
    public static void assertFaulted(Hash256 tx, Neow3j neow3j) throws IOException {
        assertThat(executionOf(tx, neow3j).getState()).isEqualTo(NeoVMStateType.FAULT);
    }

    /** Asserts that a test invocation of a read method faults with {@code message}. */
    public static void assertReadAborts(SmartContract contract, String method, String message,
            ContractParameter... params) throws IOException {
        InvocationResult result = contract.callInvokeFunction(method, List.of(params)).getInvocationResult();
        assertThat(result.getState()).isEqualTo(NeoVMStateType.FAULT);
        assertThat(result.getException()).contains(message);
    }

    /** The return value of a test invocation, which costs nothing and changes nothing. */
    public static StackItem read(SmartContract contract, String method, ContractParameter... params)
            throws IOException {
        return contract.callInvokeFunction(method, List.of(params)).getInvocationResult().getStack().get(0);
    }

    /** The deploy data of a guestbook owned by {@code owner} whose vouchers come from {@code gate}. */
    public static ContractParameter ownerAndGate(Hash160 owner, ECKeyPair gate) {
        return array(hash160(owner), publicKey(gate.getPublicKey().getEncoded(true)));
    }

    /** The voucher {@code gate} gives {@code wallet} for {@code contract}: a signature over both, as base64 of r and s. */
    public static ContractParameter voucher(Hash160 contract, Hash160 wallet, ECKeyPair gate) {
        byte[] message = ArrayUtils.concatenate(contract.toLittleEndianArray(), wallet.toLittleEndianArray());
        return string(Base64.getEncoder().encodeToString(Sign.signMessage(message, gate).getConcatenated()));
    }

    private static NeoApplicationLog.Execution executionOf(Hash256 tx, Neow3j neow3j) throws IOException {
        Await.waitUntilTransactionIsExecuted(tx, neow3j);
        return neow3j.getApplicationLog(tx).send().getApplicationLog().getExecutions().get(0);
    }
}
