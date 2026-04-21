package com.contentgrid.appserver.infrastructure.impl.fs.zip;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contentgrid.appserver.infrastructure.api.AbstractArtifactTest;
import com.contentgrid.appserver.infrastructure.api.Artifact;
import com.contentgrid.appserver.infrastructure.api.ArtifactException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZipArtifactTest extends AbstractArtifactTest {

    @TempDir
    static Path tempDir;

    static Path zipPath;
    static ZipArtifact artifact;

    @BeforeAll
    static void setup() throws IOException {
        zipPath = tempDir.resolve("test.zip");
        try (var zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            addEntry(zos, "config/a.yaml", "key: a");
            addEntry(zos, "config/b.yaml", "key: b");
            addEntry(zos, "config/sub/c.yaml", "key: c");
            addEntry(zos, "file.txt", "hello");
        }
        artifact = new ZipArtifact(zipPath);
    }

    private static void addEntry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes());
        zos.closeEntry();
    }

    @Override
    protected Artifact getArtifact() {
        return artifact;
    }

    @Test
    void loadAll_nonExistentZip_throwsArtifactException() {
        var missing = new ZipArtifact(tempDir.resolve("missing.zip"));
        assertThatThrownBy(() -> missing.loadAll(Path.of("")))
                .isInstanceOf(ArtifactException.class);
    }

    @Test
    void loadAll_unreadableZip_throwsArtifactException() throws IOException {
        Assumptions.assumeTrue(Files.getFileAttributeView(tempDir, PosixFileAttributeView.class) != null,
                "Skipping on non-POSIX filesystem");
        Assumptions.assumeFalse("root".equals(System.getProperty("user.name")),
                "Skipping when running as root");

        var unreadablePath = tempDir.resolve("unreadable.zip");
        try (var zos = new ZipOutputStream(new FileOutputStream(unreadablePath.toFile()))) {
            addEntry(zos, "file.txt", "hello");
        }
        var lockedArtifact = new ZipArtifact(unreadablePath);
        Files.setPosixFilePermissions(unreadablePath, PosixFilePermissions.fromString("---------"));
        try {
            assertThatThrownBy(() -> lockedArtifact.loadAll(Path.of("")))
                    .isInstanceOf(ArtifactException.class);
        } finally {
            // Restore permissions, so that Junit can clean up the temp zip
            Files.setPosixFilePermissions(unreadablePath, PosixFilePermissions.fromString("rw-r--r--"));
        }
    }
}
