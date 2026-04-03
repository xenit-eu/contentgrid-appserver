package com.contentgrid.appserver.infrastructure.impl.fs.directory;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.infrastructure.api.ArtifactException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemDirectoryArtifactTest {

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

    @Test
    void load_readsFileContents() throws Exception {
        var entry = artifact.load("file.txt");
        try (var stream = entry.getInputStream()) {
            assertThat(stream).hasContent("hello");
        }
    }

    @Test
    void loadAll_listsRecursively() throws ArtifactException {
        var entries = artifact.loadAll("config");

        assertThat(entries).hasSize(3);
    }

    @Test
    void loadAll_onSingleFile_returnsThatFile() throws ArtifactException {
        var entries = artifact.loadAll("file.txt");

        assertThat(entries).hasSize(1);
    }

    @Test
    void loadAll_onMissingDirectory_returnsEmptyList() throws ArtifactException {
        var entries = artifact.loadAll("nonexistent");

        assertThat(entries).isEmpty();
    }
}
