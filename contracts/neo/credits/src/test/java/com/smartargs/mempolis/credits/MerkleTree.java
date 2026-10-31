package com.smartargs.mempolis.credits;

import io.neow3j.crypto.Hash;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;
import io.neow3j.utils.BigIntegers;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.byteArray;

/** The tree the weekly job builds: tagged leaves, padded with empty leaves to a power of two. */
final class MerkleTree {

    private static final byte TAG_LEAF = 0;
    private static final byte TAG_BRANCH = 1;

    private final List<Hash160> wallets;
    private final List<List<byte[]>> levels = new ArrayList<>();

    MerkleTree(Map<Hash160, BigInteger> earned) {
        wallets = new ArrayList<>(earned.keySet());
        List<byte[]> level = new ArrayList<>();
        wallets.forEach(wallet -> level.add(leaf(wallet, earned.get(wallet))));
        while (Integer.bitCount(level.size()) != 1) {
            level.add(new byte[32]);
        }
        levels.add(level);
        while (levels.get(levels.size() - 1).size() > 1) {
            levels.add(parents(levels.get(levels.size() - 1)));
        }
    }

    byte[] root() {
        return levels.get(levels.size() - 1).get(0);
    }

    int index(Hash160 wallet) {
        return wallets.indexOf(wallet);
    }

    List<byte[]> proof(Hash160 wallet) {
        List<byte[]> proof = new ArrayList<>();
        int position = index(wallet);
        for (List<byte[]> level : levels.subList(0, levels.size() - 1)) {
            proof.add(level.get(position ^ 1));
            position /= 2;
        }
        return proof;
    }

    static ContractParameter proofParam(List<byte[]> proof) {
        return array(proof.stream().map(ContractParameter::byteArray).toList());
    }

    private static List<byte[]> parents(List<byte[]> level) {
        List<byte[]> parents = new ArrayList<>();
        for (int i = 0; i < level.size(); i += 2) {
            parents.add(Hash.sha256(concat(new byte[]{TAG_BRANCH}, level.get(i), level.get(i + 1))));
        }
        return parents;
    }

    private static byte[] leaf(Hash160 wallet, BigInteger earned) {
        return Hash.sha256(concat(new byte[]{TAG_LEAF}, wallet.toLittleEndianArray(),
                BigIntegers.toLittleEndianByteArray(earned)));
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            joined.writeBytes(part);
        }
        return joined.toByteArray();
    }
}
