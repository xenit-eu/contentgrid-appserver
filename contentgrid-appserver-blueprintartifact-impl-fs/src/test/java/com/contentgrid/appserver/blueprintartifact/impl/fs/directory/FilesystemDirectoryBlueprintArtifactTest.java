package com.contentgrid.appserver.blueprintartifact.impl.fs.directory;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contentgrid.appserver.domain.spi.blueprintartifact.AbstractBlueprintArtifactTest;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemDirectoryBlueprintArtifactTest extends AbstractBlueprintArtifactTest {

    @TempDir
    static Path tempDir;

    static FilesystemDirectoryBlueprintArtifact blueprintArtifact;

    @BeforeAll
    static void setup() throws IOException {
        blueprintArtifact = new FilesystemDirectoryBlueprintArtifact(tempDir);
        Files.writeString(tempDir.resolve("file.txt"), "hello");

        Files.createDirectories(tempDir.resolve("config/sub"));
        Files.writeString(tempDir.resolve("config/a.yaml"), "key: a");
        Files.writeString(tempDir.resolve("config/b.yaml"), "key: b");
        Files.writeString(tempDir.resolve("config/sub/c.yaml"), "key: c");
    }

    @Override
    protected BlueprintArtifact getBlueprintArtifact() {
        return blueprintArtifact;
    }

    @Test
    void loadAll_unreadableDirectory_throwsBlueprintArtifactException() throws IOException {
        Assumptions.assumeTrue(Files.getFileAttributeView(tempDir, PosixFileAttributeView.class) != null,
                "Skipping on non-POSIX filesystem");
        Assumptions.assumeFalse("root".equals(System.getProperty("user.name")),
                "Skipping when running as root");

        var unreadableDir = Files.createTempDirectory(tempDir, "unreadable");
        var lockedBlueprintArtifact = new FilesystemDirectoryBlueprintArtifact(unreadableDir);
        Files.setPosixFilePermissions(unreadableDir, PosixFilePermissions.fromString("---------"));
        try {
            assertThatThrownBy(() -> lockedBlueprintArtifact.loadAll(Path.of("")))
                    .isInstanceOf(BlueprintArtifactException.class);
        } finally {
            // Restore permissions, so that Junit can clean up the temp dir
            Files.setPosixFilePermissions(unreadableDir, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }
}
