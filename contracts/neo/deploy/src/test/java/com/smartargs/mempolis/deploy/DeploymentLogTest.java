package com.smartargs.mempolis.deploy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentLogTest {

    private static final String CONTRACT = "MempolisFoundersGuestbook";

    @TempDir
    private Path dir;

    private static Deployment deployment(String hash, String transaction) {
        return new Deployment(hash, transaction, "owner", "deployer", 1L, "fingerprint", "commit");
    }

    private Path file() {
        return dir.resolve("deployments").resolve("testnet.json");
    }

    @Test
    void aMissingFileIsAnEmptyLog() throws IOException {
        assertThat(DeploymentLog.open(file()).find(CONTRACT)).isEmpty();
    }

    @Test
    void aRecordSurvivesReopening() throws IOException {
        DeploymentLog.open(file()).record(CONTRACT, deployment("0xa", "0xt"));

        assertThat(DeploymentLog.open(file()).find(CONTRACT)).contains(deployment("0xa", "0xt"));
    }

    @Test
    void findingAContractOnChainKeepsItsTransaction() throws IOException {
        DeploymentLog.open(file()).record(CONTRACT, deployment("0xa", "0xt"));

        DeploymentLog.open(file()).record(CONTRACT, deployment("0xa", null));

        assertThat(DeploymentLog.open(file()).find(CONTRACT)).contains(deployment("0xa", "0xt"));
    }

    @Test
    void aNewAddressReplacesTheOldEntry() throws IOException {
        DeploymentLog.open(file()).record(CONTRACT, deployment("0xa", "0xt"));

        DeploymentLog.open(file()).record(CONTRACT, deployment("0xb", null));

        assertThat(DeploymentLog.open(file()).find(CONTRACT)).contains(deployment("0xb", null));
    }
}
