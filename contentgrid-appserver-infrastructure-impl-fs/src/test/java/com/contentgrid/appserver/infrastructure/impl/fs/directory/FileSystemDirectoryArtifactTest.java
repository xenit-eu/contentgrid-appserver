package com.contentgrid.appserver.infrastructure.impl.fs.directory;

import com.contentgrid.appserver.infrastructure.api.AbstractArtifactTest;
import com.contentgrid.appserver.infrastructure.api.Artifact;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;

class FileSystemDirectoryArtifactTest extends AbstractArtifactTest {

    @TempDir
    static Path tempDir;

    static FileSystemDirectoryArtifact artifact;

    @BeforeAll
    static void setup() throws IOException {
        artifact = new FileSystemDirectoryArtifact(tempDir);
        Files.writeString(tempDir.resolve("file.txt"), "hello");

        Files.createDirectories(tempDir.resolve("config/sub"));
        Files.writeString(tempDir.resolve("config/a.yml"), "key: a");
        Files.writeString(tempDir.resolve("config/b.yml"), "key: b");
        Files.writeString(tempDir.resolve("config/sub/c.yml"), "key: c");
    }

    @Override
    protected Artifact getArtifact() {
        return artifact;
    }
}
