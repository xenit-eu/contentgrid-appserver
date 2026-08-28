package com.contentgrid.appserver.actuator.policy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

/**
 * Packs a rego policy into an OPA bundle: a gzipped tarball holding the policy file and a {@code .manifest}
 * that scopes the bundle to the roots the policy owns.
 * <p>
 * The archive is written with a fixed modification time so that the same policy always produces the same bytes.
 */
class OpaBundleBuilder {

    private static final String POLICY_ENTRY_NAME = "policy.rego";
    private static final String MANIFEST_ENTRY_NAME = ".manifest";

    byte[] build(String policy, String policyPackage) throws IOException {
        var manifest = "{\"roots\": [\"%s\"]}".formatted(toBundleRoot(policyPackage));
        var output = new ByteArrayOutputStream();

        try (var archive = new TarArchiveOutputStream(new GzipCompressorOutputStream(output))) {
            addEntry(archive, MANIFEST_ENTRY_NAME, manifest.getBytes(StandardCharsets.UTF_8));
            addEntry(archive, POLICY_ENTRY_NAME, policy.getBytes(StandardCharsets.UTF_8));
            archive.finish();
        }

        return output.toByteArray();
    }

    /**
     * Translates a rego package name into the data path that the bundle claims ownership of,
     * e.g. {@code contentgrid.appserver} becomes {@code contentgrid/appserver}.
     */
    private static String toBundleRoot(String policyPackage) {
        return policyPackage.replace('.', '/');
    }

    private static void addEntry(TarArchiveOutputStream archive, String name, byte[] contents) throws IOException {
        var entry = new TarArchiveEntry(name);
        entry.setSize(contents.length);
        entry.setModTime(0);
        archive.putArchiveEntry(entry);
        archive.write(contents);
        archive.closeArchiveEntry();
    }
}
