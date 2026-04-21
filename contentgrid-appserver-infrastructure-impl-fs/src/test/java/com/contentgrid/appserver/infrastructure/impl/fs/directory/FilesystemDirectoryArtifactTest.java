package com.contentgrid.appserver.infrastructure.impl.fs.directory;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contentgrid.appserver.infrastructure.api.AbstractArtifactTest;
import com.contentgrid.appserver.infrastructure.api.Artifact;
import com.contentgrid.appserver.infrastructure.api.ArtifactException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemDirectoryArtifactTest extends AbstractArtifactTest {

    @TempDir
    static Path tempDir;

    static FilesystemDirectoryArtifact artifact;

    @BeforeAll
    static void setup() throws IOException {
        artifact = new FilesystemDirectoryArtifact(tempDir);
        Files.writeString(tempDir.resolve("file.txt"), "hello");

        Files.createDirectories(tempDir.resolve("config/sub"));
        Files.writeString(tempDir.resolve("config/a.yaml"), "key: a");
        Files.writeString(tempDir.resolve("config/b.yaml"), "key: b");
        Files.writeString(tempDir.resolve("config/sub/c.yaml"), "key: c");
    }

    @Override
    protected Artifact getArtifact() {
        return artifact;
    }

    @Test
    void loadAll_unreadableDirectory_throwsArtifactException() throws IOException {
        Assumptions.assumeTrue(Files.getFileAttributeView(tempDir, PosixFileAttributeView.class) != null,
                "Skipping on non-POSIX filesystem");
        Assumptions.assumeFalse("root".equals(System.getProperty("user.name")),
                "Skipping when running as root");

        var unreadableDir = Files.createTempDirectory(tempDir, "unreadable");
        var lockedArtifact = new FilesystemDirectoryArtifact(unreadableDir);
        Files.setPosixFilePermissions(unreadableDir, PosixFilePermissions.fromString("---------"));
        try {
            assertThatThrownBy(() -> lockedArtifact.loadAll(Path.of("")))
                    .isInstanceOf(ArtifactException.class);
        } finally {
            // Restore permissions, so that Junit can clean up the temp dir
            Files.setPosixFilePermissions(unreadableDir, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }
}
