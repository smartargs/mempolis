package com.smartargs.mempolis.deed;

import io.neow3j.contract.GasToken;
import io.neow3j.contract.SmartContract;
import io.neow3j.crypto.ECKeyPair;
import io.neow3j.protocol.Neow3j;
import io.neow3j.transaction.AccountSigner;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;
import io.neow3j.types.Hash256;
import io.neow3j.wallet.Account;

import java.math.BigInteger;
import java.util.Arrays;

import static com.smartargs.mempolis.testkit.TestHelper.assertHalted;
import static com.smartargs.mempolis.testkit.TestHelper.send;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.byteArray;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;
import static io.neow3j.types.ContractParameter.publicKey;

/** A small city plan and the calls the deed tests share. */
final class DeedFixture {

    static final int STANDARD = 1;
    static final int CHOICE = 2;
    static final int PRIME = 3;
    static final int VIP = 4;

    static final int PRICE_NEWCOMER = 1;
    static final int PRICE_CHOICE = 2;
    static final int PRICE_PRIME = 3;
    static final int PRICE_RESTYLE = 4;

    static final int STYLES = 4;

    static final BigInteger NEWCOMER_FEE = BigInteger.valueOf(50_000_000);
    static final BigInteger CHOICE_PRICE = BigInteger.valueOf(300_000_000);
    static final BigInteger PRIME_PRICE = BigInteger.valueOf(900_000_000);
    static final BigInteger RESTYLE_PRICE = BigInteger.valueOf(20_000_000);

    static final int[][] PLAN = plan();

    static final int[] QUEUE = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
            28, 29, 30, 31, 32, 33, 34};

    static final int RING_0_CHOICE = 21;
    static final int RING_0_PRIME = 25;
    static final int RING_0_VIP = 27;
    static final int RING_0_STANDARD = 5;
    static final int RING_2_CHOICE = 39;
    static final int RING_2_STANDARD = 40;

    private DeedFixture() {}

    private static int[][] plan() {
        int[][] plan = new int[40][];
        for (int lot = 1; lot <= 40; lot++) {
            int tier = lot <= 20 || (lot >= 28 && lot <= 34) || lot == 40 ? STANDARD
                    : (lot <= 24 || (lot >= 35 && lot <= 37) || lot == 39) ? CHOICE
                    : lot == 27 ? VIP : PRIME;
            int ring = lot <= 27 ? 0 : lot <= 38 ? 1 : 2;
            plan[lot - 1] = new int[]{tier, ring};
        }
        return plan;
    }

    static ContractParameter deployData(Hash160 owner, ECKeyPair gate, Hash160 guestbook, Hash160 treasury, int styles) {
        return array(hash160(owner), publicKey(gate.getPublicKey().getEncoded(true)), hash160(guestbook),
                hash160(treasury), integer(styles));
    }

    static byte info(int tier, int ring) {
        return (byte) (tier + 8 * ring);
    }

    static ContractParameter infos(int from, int to) {
        byte[] bytes = new byte[to - from + 1];
        for (int lot = from; lot <= to; lot++) bytes[lot - from] = info(PLAN[lot - 1][0], PLAN[lot - 1][1]);
        return byteArray(bytes);
    }

    static ContractParameter lots(int... lots) {
        return array(Arrays.stream(lots).mapToObj(ContractParameter::integer).toArray(ContractParameter[]::new));
    }

    static void setUpCity(SmartContract deed, Account owner, Neow3j neow3j) throws Throwable {
        assertHalted(send(deed, "registerLots", owner, integer(1), infos(1, 40)), neow3j);
        assertHalted(send(deed, "queueLots", owner, integer(1), lots(QUEUE)), neow3j);
        setPrices(deed, owner, neow3j);
    }

    static void setPrices(SmartContract deed, Account owner, Neow3j neow3j) throws Throwable {
        assertHalted(send(deed, "setPrice", owner, integer(PRICE_NEWCOMER), integer(NEWCOMER_FEE)), neow3j);
        assertHalted(send(deed, "setPrice", owner, integer(PRICE_CHOICE), integer(CHOICE_PRICE)), neow3j);
        assertHalted(send(deed, "setPrice", owner, integer(PRICE_PRIME), integer(PRIME_PRICE)), neow3j);
        assertHalted(send(deed, "setPrice", owner, integer(PRICE_RESTYLE), integer(RESTYLE_PRICE)), neow3j);
    }

    static Hash256 pay(Neow3j neow3j, SmartContract deed, Account payer, BigInteger amount, ContractParameter data)
            throws Throwable {
        return new GasToken(neow3j).transfer(payer, deed.getScriptHash(), amount, data)
                .signers(AccountSigner.calledByEntry(payer))
                .sign().send().getSendRawTransaction().getHash();
    }

    static ContractParameter deedVoucher(Neow3j neow3j, Hash160 deed, Hash160 wallet, ECKeyPair gate) throws Throwable {
        byte[] network = io.neow3j.utils.BigIntegers.toLittleEndianByteArray(BigInteger.valueOf(neow3j.getNetworkMagic()));
        byte[] message = io.neow3j.utils.ArrayUtils.concatenate(deed.toLittleEndianArray(), wallet.toLittleEndianArray(), network);
        return io.neow3j.types.ContractParameter.string(java.util.Base64.getEncoder()
                .encodeToString(io.neow3j.crypto.Sign.signMessage(message, gate).getConcatenated()));
    }

    static ContractParameter newcomer() {
        return array(integer(1));
    }

    static ContractParameter chosen(int lot, int style) {
        return array(integer(2), integer(lot), integer(style));
    }

    static ContractParameter restyle(int style) {
        return array(integer(3), integer(style));
    }
}
