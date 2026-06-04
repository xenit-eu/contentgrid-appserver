package com.contentgrid.appserver.infrastructure.impl.fs.zip;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contentgrid.appserver.infrastructure.api.AbstractBlueprintArtifactTest;
import com.contentgrid.appserver.infrastructure.api.BlueprintArtifact;
import com.contentgrid.appserver.infrastructure.api.BlueprintArtifactException;
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

class ZipBlueprintArtifactTest extends AbstractBlueprintArtifactTest {

    @TempDir
    static Path tempDir;

    static Path zipPath;
    static ZipBlueprintArtifact blueprintArtifact;

    @BeforeAll
    static void setup() throws IOException {
        zipPath = tempDir.resolve("test.zip");
        try (var zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            addEntry(zos, "config/a.yaml", "key: a");
            addEntry(zos, "config/b.yaml", "key: b");
            addEntry(zos, "config/sub/c.yaml", "key: c");
            addEntry(zos, "file.txt", "hello");
        }
        blueprintArtifact = new ZipBlueprintArtifact(zipPath);
    }

    private static void addEntry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes());
        zos.closeEntry();
    }

    @Override
    protected BlueprintArtifact getBlueprintArtifact() {
        return blueprintArtifact;
    }

    @Test
    void loadAll_nonExistentZip_throwsBlueprintArtifactException() {
        var missing = new ZipBlueprintArtifact(tempDir.resolve("missing.zip"));
        assertThatThrownBy(() -> missing.loadAll(Path.of("")))
                .isInstanceOf(BlueprintArtifactException.class);
    }

    @Test
    void loadAll_unreadableZip_throwsBlueprintArtifactException() throws IOException {
        Assumptions.assumeTrue(Files.getFileAttributeView(tempDir, PosixFileAttributeView.class) != null,
                "Skipping on non-POSIX filesystem");
        Assumptions.assumeFalse("root".equals(System.getProperty("user.name")),
                "Skipping when running as root");

        var unreadablePath = tempDir.resolve("unreadable.zip");
        try (var zos = new ZipOutputStream(new FileOutputStream(unreadablePath.toFile()))) {
            addEntry(zos, "file.txt", "hello");
        }
        var lockedBlueprintArtifact = new ZipBlueprintArtifact(unreadablePath);
        Files.setPosixFilePermissions(unreadablePath, PosixFilePermissions.fromString("---------"));
        try {
            assertThatThrownBy(() -> lockedBlueprintArtifact.loadAll(Path.of("")))
                    .isInstanceOf(BlueprintArtifactException.class);
        } finally {
            // Restore permissions, so that Junit can clean up the temp zip
            Files.setPosixFilePermissions(unreadablePath, PosixFilePermissions.fromString("rw-r--r--"));
        }
    }
}
