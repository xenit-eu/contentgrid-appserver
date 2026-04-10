package com.contentgrid.appserver.infrastructure.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Abstract test class for testing {@link Artifact}. Each implementation should set up an artifact
 * with the following resources:
 * <ul>
 *     <li>file.txt</li>
 *     <li>config/a.yaml</li>
 *     <li>config/b.yaml</li>
 *     <li>config/sub/c.yaml</li>
 * </ul>
 */
public abstract class AbstractArtifactTest {

    protected abstract Artifact getArtifact();


    @Test
    void load_readsFileContents() throws Exception {
        var artifact = getArtifact();
        var entry = artifact.load(Path.of("file.txt"));
        try (var stream = entry.getInputStream()) {
            assertThat(stream).hasContent("hello");
        }
    }

    @Test
    void loadAll_listsRecursively() throws ArtifactException {
        var artifact = getArtifact();
        var entries = artifact.loadAll(Path.of("config"));

        assertThat(entries)
                .map(ArtifactEntry::getEntryReference)
                .map(ArtifactEntryReference::getRelativePath)
                .containsExactlyInAnyOrder("config/a.yaml", "config/b.yaml", "config/sub/c.yaml");
    }

    @Test
    void loadAll_onSingleFile_returnsThatFile() throws ArtifactException {
        var artifact = getArtifact();
        var entries = artifact.loadAll(Path.of("file.txt"));

        assertThat(entries).singleElement().satisfies(entry ->
                assertThat(entry.getEntryReference().getRelativePath()).isEqualTo("file.txt"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "."})
    void loadAll_rootPath_listsAllEntries(String root) throws ArtifactException {
        var artifact = getArtifact();
        var entries = artifact.loadAll(Path.of(root));

        assertThat(entries)
                .map(ArtifactEntry::getEntryReference)
                .map(ArtifactEntryReference::getRelativePath)
                .containsExactlyInAnyOrder("config/a.yaml", "config/b.yaml", "config/sub/c.yaml", "file.txt");
    }

    @Test
    void load_missingEntry_throwsArtifactEntryNotFoundException() {
        var artifact = getArtifact();
        assertThatThrownBy(() -> artifact.load(Path.of("nonexistent.txt")))
                .isInstanceOf(ArtifactEntryNotFoundException.class);
    }

    @Test
    void loadAll_onMissingDirectory_returnsEmptyList() throws ArtifactException {
        var artifact = getArtifact();
        var entries = artifact.loadAll(Path.of("nonexistent"));

        assertThat(entries).isEmpty();
    }

}
