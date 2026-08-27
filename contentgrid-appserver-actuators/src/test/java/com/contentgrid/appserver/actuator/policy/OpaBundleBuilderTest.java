package com.contentgrid.appserver.actuator.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OpaBundleBuilderTest {

    private final OpaBundleBuilder builder = new OpaBundleBuilder();

    /**
     * The entry names and the manifest shape are what makes the archive an OPA bundle,
     * so they are asserted exhaustively.
     */
    @Test
    void bundleHoldsOnlyAManifestAndThePolicy() throws IOException {
        var bundle = unpack(builder.build("package contentgrid.appserver", "contentgrid.appserver"));

        assertThat(bundle).containsOnly(
                entry(".manifest", "{\"roots\": [\"contentgrid/appserver\"]}"),
                entry("policy.rego", "package contentgrid.appserver"));
    }

    @ParameterizedTest
    @CsvSource({
            "contentgrid.appserver, contentgrid/appserver",
            "xfb0e9318f3894300a64edba3532e6ac0, xfb0e9318f3894300a64edba3532e6ac0"
    })
    void policyPackageBecomesTheBundleRoot(String policyPackage, String expectedRoot) throws IOException {
        var bundle = unpack(builder.build("package " + policyPackage, policyPackage));

        assertThat(bundle).containsEntry(".manifest", "{\"roots\": [\"%s\"]}".formatted(expectedRoot));
    }

    private static Map<String, String> unpack(byte[] bundle) throws IOException {
        var entries = new LinkedHashMap<String, String>();
        try (var archive = new TarArchiveInputStream(new GzipCompressorInputStream(new ByteArrayInputStream(bundle)))) {
            TarArchiveEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(archive.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }
}
