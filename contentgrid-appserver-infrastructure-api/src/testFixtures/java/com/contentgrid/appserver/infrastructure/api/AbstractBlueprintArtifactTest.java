package com.contentgrid.appserver.infrastructure.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Abstract test class for testing {@link BlueprintArtifact}. Each implementation should set up
 * a {@link BlueprintArtifact} with the following resources:
 * <ul>
 *     <li>file.txt</li>
 *     <li>config/a.yaml</li>
 *     <li>config/b.yaml</li>
 *     <li>config/sub/c.yaml</li>
 * </ul>
 */
public abstract class AbstractBlueprintArtifactTest {

    protected abstract BlueprintArtifact getBlueprintArtifact();


    @Test
    void load_readsFileContents() throws Exception {
        var blueprintArtifact = getBlueprintArtifact();
        var maybeItem = blueprintArtifact.load(Path.of("file.txt"));
        assertThat(maybeItem).hasValueSatisfying(item -> {
            assertThat(item.getItemReference().getBlueprintArtifactReference()).isEqualTo(blueprintArtifact.getReference());
            try (var stream = item.getInputStream()) {
                assertThat(stream).hasContent("hello");
            } catch (BlueprintArtifactItemUnreadableException | IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void loadAll_listsRecursively() throws BlueprintArtifactException {
        var blueprintArtifact = getBlueprintArtifact();
        var items = blueprintArtifact.loadAll(Path.of("config"));

        assertThat(items)
                .map(BlueprintArtifactItem::getItemReference)
                .map(BlueprintArtifactItemReference::getPath)
                .containsExactlyInAnyOrder("config/a.yaml", "config/b.yaml", "config/sub/c.yaml");
    }

    @Test
    void loadAll_onSingleFile_returnsThatFile() throws BlueprintArtifactException {
        var blueprintArtifact = getBlueprintArtifact();
        var items = blueprintArtifact.loadAll(Path.of("file.txt"));

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.getItemReference().getBlueprintArtifactReference()).isEqualTo(blueprintArtifact.getReference());
            assertThat(item.getItemReference().getPath()).isEqualTo("file.txt");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "."})
    void loadAll_rootPath_listsAllEntries(String root) throws BlueprintArtifactException {
        var blueprintArtifact = getBlueprintArtifact();
        var items = blueprintArtifact.loadAll(Path.of(root));

        assertThat(items)
                .map(BlueprintArtifactItem::getItemReference)
                .map(BlueprintArtifactItemReference::getPath)
                .containsExactlyInAnyOrder("config/a.yaml", "config/b.yaml", "config/sub/c.yaml", "file.txt");
    }

    @Test
    void load_missingEntry_returnsEmptyOptional() throws BlueprintArtifactException {
        var blueprintArtifact = getBlueprintArtifact();
        assertThat(blueprintArtifact.load(Path.of("nonexistent.txt"))).isEmpty();
    }

    @Test
    void loadRequired_missingEntry_throwsBlueprintArtifactEntryNotFoundException() {
        var blueprintArtifact = getBlueprintArtifact();
        assertThatThrownBy(() -> blueprintArtifact.loadRequired(Path.of("nonexistent.txt")))
                .isInstanceOf(BlueprintArtifactItemNotFoundException.class);
    }

    @Test
    void loadAll_onMissingDirectory_returnsEmptyList() throws BlueprintArtifactException {
        var blueprintArtifact = getBlueprintArtifact();
        var items = blueprintArtifact.loadAll(Path.of("nonexistent"));

        assertThat(items).isEmpty();
    }

}
