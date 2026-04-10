package com.contentgrid.appserver.infrastructure.impl.fs.directory;

import com.contentgrid.appserver.infrastructure.api.AbstractArtifactTest;
import com.contentgrid.appserver.infrastructure.api.Artifact;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
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
}
