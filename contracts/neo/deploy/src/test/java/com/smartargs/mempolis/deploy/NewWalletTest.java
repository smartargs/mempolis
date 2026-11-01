package com.smartargs.mempolis.deploy;

import io.neow3j.wallet.Account;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NewWalletTest {

    @TempDir
    private Path dir;

    private Path file() {
        return dir.resolve("keys").resolve("owner.wif");
    }

    @Test
    void theStoredKeyOpensTheReturnedAddress() throws IOException {
        String address = NewWallet.create(file());

        assertThat(Account.fromWIF(Files.readString(file()).trim()).getAddress()).isEqualTo(address);
        assertThat(address).startsWith("N");
    }

    @Test
    void onlyTheUserCanReadTheKey() throws IOException {
        NewWallet.create(file());

        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file()))).isEqualTo("rw-------");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file().getParent()))).isEqualTo("rwx------");
    }

    @Test
    void anExistingKeyIsNeverOverwritten() throws IOException {
        NewWallet.create(file());
        String kept = Files.readString(file());

        assertThatThrownBy(() -> NewWallet.create(file())).hasMessageContaining("already exists");
        assertThat(Files.readString(file())).isEqualTo(kept);
    }

    @Test
    void everyWalletIsNew() throws IOException {
        assertThat(NewWallet.create(file())).isNotEqualTo(NewWallet.create(dir.resolve("keys").resolve("deployer.wif")));
    }
}
