package com.smartargs.mempolis.deploy;

import io.neow3j.contract.NefFile;
import io.neow3j.crypto.Hash;
import io.neow3j.protocol.ObjectMapperFactory;
import io.neow3j.protocol.core.response.ContractManifest;
import io.neow3j.serialization.exceptions.DeserializationException;
import io.neow3j.utils.Numeric;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * A compiled contract, ready to deploy.
 *
 * @param nef the contract's code
 * @param manifest the contract's manifest
 */
public record ContractArtifact(NefFile nef, ContractManifest manifest) {

    private static final String NEF_SUFFIX = ".nef";
    private static final String MANIFEST_SUFFIX = ".manifest.json";

    /** Loads what {@code neow3jCompile} wrote for the Gradle module at {@code module}. */
    public static ContractArtifact loadFrom(Path module) throws IOException, DeserializationException {
        Path output = module.resolve("build").resolve("neow3j");
        Path nef = onlyNef(output);
        String name = nef.getFileName().toString().replace(NEF_SUFFIX, "");
        ContractManifest manifest = ObjectMapperFactory.getObjectMapper()
                .readValue(output.resolve(name + MANIFEST_SUFFIX).toFile(), ContractManifest.class);
        return new ContractArtifact(NefFile.readFromFile(nef.toFile()), manifest);
    }

    /** The contract's name, which is part of its address. */
    public String name() {
        return manifest.getName();
    }

    /**
     * A hash of the code and the manifest together. The NEF checksum alone misses a change that only touches the
     * manifest, such as a new permission.
     */
    public String fingerprint() {
        try {
            byte[] manifestJson = ObjectMapperFactory.getObjectMapper().writeValueAsString(manifest)
                    .getBytes(StandardCharsets.UTF_8);
            return Numeric.toHexStringNoPrefix(Hash.sha256(concat(nef.toArray(), manifestJson)));
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    private static Path onlyNef(Path output) throws IOException {
        if (!Files.isDirectory(output)) throw new IllegalStateException("Nothing compiled in " + output);
        List<Path> nefs = new ArrayList<>();
        try (Stream<Path> files = Files.list(output)) {
            files.filter(file -> file.getFileName().toString().endsWith(NEF_SUFFIX)).forEach(nefs::add);
        }
        if (nefs.size() != 1) throw new IllegalStateException("Expected one NEF in " + output + ": " + nefs);
        return nefs.get(0);
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] joined = new byte[first.length + second.length];
        System.arraycopy(first, 0, joined, 0, first.length);
        System.arraycopy(second, 0, joined, first.length, second.length);
        return joined;
    }
}
