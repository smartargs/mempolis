package com.smartargs.mempolis.deploy;

import io.neow3j.crypto.ECKeyPair;
import io.neow3j.types.Hash160;
import io.neow3j.utils.Numeric;
import io.neow3j.wallet.Account;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptionsTest {

    private static Options parse(String... args) {
        return Options.parse(args);
    }

    @Test
    void allMeansEveryContractAndDefaultsStaySafe() {
        Options options = parse("testnet", "all");

        assertThat(options.network()).isEqualTo(Network.TESTNET);
        assertThat(options.modules()).containsExactly("guestbook", "credits", "deed");
        assertThat(options.owner()).isEmpty();
        assertThat(options.rpc()).isEqualTo(Network.TESTNET.defaultRpc());
        assertThat(options.dryRun()).isFalse();
    }

    @Test
    void readsEveryFlag() {
        Hash160 owner = Account.create().getScriptHash();

        Options options = parse("mainnet", "credits", "--owner", owner.toAddress(), "--rpc", "http://node", "--dry-run",
                "--commit", "abc");

        assertThat(options.network()).isEqualTo(Network.MAINNET);
        assertThat(options.modules()).isEqualTo(List.of("credits"));
        assertThat(options.owner()).contains(owner);
        assertThat(options.rpc()).isEqualTo("http://node");
        assertThat(options.dryRun()).isTrue();
        assertThat(options.commit()).isEqualTo("abc");
    }

    @Test
    void readsTheGateKey() {
        ECKeyPair.ECPublicKey gate = Account.create().getECKeyPair().getPublicKey();

        Options options = parse("testnet", "guestbook", "--gate", Numeric.toHexStringNoPrefix(gate.getEncoded(true)));

        assertThat(options.gate()).contains(gate);
        assertThat(parse("testnet", "credits").gate()).isEmpty();
    }

    @Test
    void refusesAGateThatIsNotAKey() {
        assertThatThrownBy(() -> parse("testnet", "guestbook", "--gate", "nonsense")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void refusesAnUnknownNetwork() {
        assertThatThrownBy(() -> parse("devnet", "all")).hasMessageContaining("Unknown network");
    }

    @Test
    void refusesAnUnknownContract() {
        assertThatThrownBy(() -> parse("testnet", "testkit")).hasMessageContaining("Unknown contract");
    }

    @Test
    void refusesAnUnknownFlagInsteadOfIgnoringIt() {
        assertThatThrownBy(() -> parse("testnet", "all", "--dryrun")).hasMessageContaining("Unknown option");
    }

    @Test
    void refusesAFlagWithoutItsValue() {
        assertThatThrownBy(() -> parse("testnet", "all", "--owner")).hasMessageContaining("--owner needs a value");
    }

    @Test
    void refusesAnOwnerThatIsNotAnAddress() {
        assertThatThrownBy(() -> parse("testnet", "all", "--owner", "nonsense"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
