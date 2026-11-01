package com.smartargs.mempolis.deploy;

import com.smartargs.mempolis.guestbook.FoundersGuestbook;
import io.neow3j.compiler.CompilationUnit;
import io.neow3j.compiler.Compiler;
import io.neow3j.types.Hash160;
import io.neow3j.wallet.Account;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MainnetGuardTest {

    private static final Hash160 DEPLOYER = Account.create().getScriptHash();
    private static final Optional<Hash160> OWNER = Optional.of(Account.create().getScriptHash());

    private static ContractArtifact artifact;

    @TempDir
    private Path dir;

    @BeforeAll
    static void compile() throws Throwable {
        CompilationUnit unit = new Compiler().compile(FoundersGuestbook.class.getCanonicalName());
        artifact = new ContractArtifact(unit.getNefFile(), unit.getManifest());
    }

    private DeploymentLog testnetWith(String fingerprint) throws IOException {
        DeploymentLog log = DeploymentLog.open(dir.resolve("testnet.json"));
        log.record(artifact.name(), new Deployment("0xa", "0xt", "owner", "deployer", 1L, fingerprint, "commit"));
        return log;
    }

    private DeploymentLog emptyTestnet() throws IOException {
        return DeploymentLog.open(dir.resolve("testnet.json"));
    }

    @Test
    void passesASeparateOwnerAndTheBuildThatRanOnTestnet() throws IOException {
        DeploymentLog testnet = testnetWith(artifact.fingerprint());

        assertThatCode(() -> MainnetGuard.check(OWNER, DEPLOYER, List.of(artifact), testnet))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesAMissingOwner() throws IOException {
        DeploymentLog testnet = testnetWith(artifact.fingerprint());

        assertThatThrownBy(() -> MainnetGuard.check(Optional.empty(), DEPLOYER, List.of(artifact), testnet))
                .hasMessageContaining("needs --owner");
    }

    @Test
    void refusesTheDeployingKeyAsOwner() throws IOException {
        DeploymentLog testnet = testnetWith(artifact.fingerprint());

        assertThatThrownBy(() -> MainnetGuard.check(Optional.of(DEPLOYER), DEPLOYER, List.of(artifact), testnet))
                .hasMessageContaining("must not be the deploying key");
    }

    @Test
    void refusesAContractNeverDeployedToTestnet() throws IOException {
        DeploymentLog testnet = emptyTestnet();

        assertThatThrownBy(() -> MainnetGuard.check(OWNER, DEPLOYER, List.of(artifact), testnet))
                .hasMessageContaining("to testnet first");
    }

    @Test
    void refusesABuildThatDiffersFromTheOneOnTestnet() throws IOException {
        DeploymentLog testnet = testnetWith("an older build");

        assertThatThrownBy(() -> MainnetGuard.check(OWNER, DEPLOYER, List.of(artifact), testnet))
                .hasMessageContaining("to testnet first");
    }
}
