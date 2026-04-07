package com.contentgrid.appserver.infrastructure.impl.fs.zip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contentgrid.appserver.infrastructure.api.ArtifactEntryNotFoundException;
import com.contentgrid.appserver.infrastructure.api.ArtifactException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.io.FileOutputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZipArtifactTest {

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

    @Test
    void load_readsEntryContents() throws Exception {
        var entry = artifact.load("config/a.yaml");
        try (var stream = entry.getInputStream()) {
            assertThat(stream).hasContent("key: a");
        }
    }

    @Test
    void load_missingEntry_throwsArtifactEntryNotFoundException() {
        assertThatThrownBy(() -> artifact.load("nonexistent.yaml"))
                .isInstanceOf(ArtifactEntryNotFoundException.class);
    }

    @Test
    void loadAll_listsRecursivelyUnderPath() throws ArtifactException {
        var entries = artifact.loadAll("config");

        assertThat(entries).hasSize(3) // a.yaml, b.yaml, sub/c.yaml
                .allSatisfy(e -> assertThat(e).isInstanceOf(ZipArtifactEntry.class));
    }

    @Test
    void loadAll_withTrailingSlash_listsRecursively() throws ArtifactException {
        var entries = artifact.loadAll("config/");

        assertThat(entries).hasSize(3);
    }

    @Test
    void loadAll_rootPath_listsAllEntries() throws ArtifactException {
        var entries = artifact.loadAll("");

        assertThat(entries).hasSize(4); // a.yaml, b.yaml, sub/c.yaml, file.txt
    }

    @Test
    void loadAll_onSingleFile_returnsThatFile() throws ArtifactException {
        var entries = artifact.loadAll("file.txt");
    }

    @Test
    void loadAll_nonExistentZip_throwsArtifactException() {
        var missing = new ZipArtifact(tempDir.resolve("missing.zip"));
        assertThatThrownBy(() -> missing.loadAll(""))
                .isInstanceOf(ArtifactException.class);
    }
}
