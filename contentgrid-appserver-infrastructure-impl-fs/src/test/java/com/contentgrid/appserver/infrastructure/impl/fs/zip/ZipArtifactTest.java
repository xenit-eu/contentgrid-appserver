package com.contentgrid.appserver.infrastructure.impl.fs.zip;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.infrastructure.api.AbstractArtifactTest;
import com.contentgrid.appserver.infrastructure.api.Artifact;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterAll;
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
            ZipUtils.addEntry(zos, "config/a.yaml", "key: a");
            ZipUtils.addEntry(zos, "config/b.yaml", "key: b");
            ZipUtils.addEntry(zos, "config/sub/c.yaml", "key: c");
            ZipUtils.addEntry(zos, "file.txt", "hello");
        }
        artifact = new ZipArtifact(zipPath);
    }

    @AfterAll
    static void cleanup() throws IOException {
        artifact.close();
    }

    @Override
    protected Artifact getArtifact() {
        return artifact;
    }

    @Test
    void createArtifact_nonExistentZip_throwsIOException() {
        var missingPath = tempDir.resolve("missing.zip");
        boolean thrown;
        try (var ignored = new ZipArtifact(missingPath)) {
            thrown = false;
        } catch (IOException e) {
            thrown = true;
        }
        assertThat(thrown).as("Creating a ZipArtifact for missing.zip should throw IOException").isTrue();
    }

    @Test
    void createArtifact_emptyZip_throwsIOException() throws IOException {
        var emptyPath = tempDir.resolve("empty.zip");
        Files.createFile(emptyPath); // empty file
        boolean thrown;
        try (var ignored = new ZipArtifact(emptyPath)) {
            thrown = false;
        } catch (IOException e) {
            thrown = true;
        }
        assertThat(thrown).as("Creating a ZipArtifact for empty.zip should throw IOException").isTrue();
    }

    @Test
    void createArtifact_unreadableZip_throwsIOException() throws IOException {
        Assumptions.assumeTrue(Files.getFileAttributeView(tempDir, PosixFileAttributeView.class) != null,
                "Skipping on non-POSIX filesystem");
        Assumptions.assumeFalse("root".equals(System.getProperty("user.name")),
                "Skipping when running as root");

        var unreadablePath = tempDir.resolve("unreadable.zip");
        ZipUtils.createZip(unreadablePath);
        Files.setPosixFilePermissions(unreadablePath, PosixFilePermissions.fromString("---------"));
        boolean thrown;
        try (var ignored = new ZipArtifact(unreadablePath)) {
            thrown = false;
        } catch (IOException e) {
            thrown = true;
        } finally {
            // Restore permissions, so that Junit can clean up the temp zip
            Files.setPosixFilePermissions(unreadablePath, PosixFilePermissions.fromString("rw-r--r--"));
        }
        assertThat(thrown).as("Creating a ZipArtifact for unreadable.zip should throw IOException").isTrue();
    }
}
