package com.smartargs.mempolis.deploy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** The file recording what is deployed on one network, keyed by contract name. */
public final class DeploymentLog {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final Path file;
    private final Map<String, Deployment> deployments;

    private DeploymentLog(Path file, Map<String, Deployment> deployments) {
        this.file = file;
        this.deployments = deployments;
    }

    /** Opens the log at {@code file}, which is empty if the file does not exist yet. */
    public static DeploymentLog open(Path file) throws IOException {
        if (!Files.exists(file)) return new DeploymentLog(file, new TreeMap<>());
        return new DeploymentLog(file, MAPPER.readValue(file.toFile(), new TypeReference<TreeMap<String, Deployment>>() {}));
    }

    /** The recorded deployment of {@code contract}, if there is one. */
    public Optional<Deployment> find(String contract) {
        return Optional.ofNullable(deployments.get(contract));
    }

    /**
     * Records {@code deployment} and saves the file. A contract found on chain does not replace the entry that already
     * names its transaction.
     */
    public void record(String contract, Deployment deployment) throws IOException {
        Deployment known = deployments.get(contract);
        boolean adds = known == null || !known.hash().equals(deployment.hash()) || deployment.transaction() != null;
        if (!adds) return;

        deployments.put(contract, deployment);
        Files.createDirectories(file.getParent());
        MAPPER.writeValue(file.toFile(), deployments);
    }
}
