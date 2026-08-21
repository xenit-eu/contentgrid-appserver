package com.contentgrid.appserver.actuator.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.junit.jupiter.api.Test;

class OpaBundleBuilderTest {

    private final OpaBundleBuilder builder = new OpaBundleBuilder();

    @Test
    void dottedPackageBecomesASlashSeparatedRoot() throws IOException {
        var bundle = unpack(builder.build("package contentgrid.appserver", "contentgrid.appserver"));

        assertThat(bundle).containsEntry(".manifest", "{\"roots\": [\"contentgrid/appserver\"]}");
    }

    @Test
    void singleSegmentPackageIsUsedAsTheRootUnchanged() throws IOException {
        var bundle = unpack(builder.build("package xfb0e9318f3894300a64edba3532e6ac0",
                "xfb0e9318f3894300a64edba3532e6ac0"));

        assertThat(bundle).containsEntry(".manifest", "{\"roots\": [\"xfb0e9318f3894300a64edba3532e6ac0\"]}");
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
