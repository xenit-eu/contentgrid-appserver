package com.contentgrid.appserver.contentstore.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ContentStoreRegistryTest {

    @Test
    void testCreateRegistryWithSingleStore() {
        var store = mock(ContentStore.class);
        var registry = new DefaultContentStoreRegistry("primary", store);

        assertThat(registry.getWriteStoreId()).isEqualTo("primary");
        assertThat(registry.getWriteStore()).isSameAs(store);
        assertThat(registry.getStore("primary")).contains(store);
        assertThat(registry.getStoreIds()).containsExactly("primary");
    }

    @Test
    void testCreateRegistryWithMultipleStores() {
        var primaryStore = mock(ContentStore.class);
        var secondaryStore = mock(ContentStore.class);
        var tertiaryStore = mock(ContentStore.class);

        var stores = Map.of(
            "primary", primaryStore,
            "secondary", secondaryStore,
            "tertiary", tertiaryStore
        );

        var registry = new DefaultContentStoreRegistry("primary", stores);

        assertThat(registry.getWriteStoreId()).isEqualTo("primary");
        assertThat(registry.getWriteStore()).isSameAs(primaryStore);
        assertThat(registry.getStore("primary")).contains(primaryStore);
        assertThat(registry.getStore("secondary")).contains(secondaryStore);
        assertThat(registry.getStore("tertiary")).contains(tertiaryStore);
        assertThat(registry.getStoreIds()).containsExactlyInAnyOrder("primary", "secondary", "tertiary");
    }

    @Test
    void testCreateRegistryWithInvalidWriteStoreId() {
        var store = mock(ContentStore.class);
        var stores = Map.of("store1", store);

        assertThatThrownBy(() -> new DefaultContentStoreRegistry("nonexistent", stores))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Write store ID 'nonexistent' not found in stores map");
    }

    @Test
    void testGetNonExistentStore() {
        var store = mock(ContentStore.class);
        var registry = new DefaultContentStoreRegistry("primary", store);

        assertThat(registry.getStore("nonexistent")).isEmpty();
    }


    @Test
    void testGetStoreForReadingWithoutStoreId() throws UnreadableContentException {
        var store = mock(ContentStore.class);
        var registry = new DefaultContentStoreRegistry("primary", store);

        var reference = ContentReference.of("content-123");
        var result = registry.getStoreForReading(reference);

        assertThat(result).isSameAs(store);
    }

    @Test
    void testGetStoreForReadingWithStoreId() throws UnreadableContentException {
        var primaryStore = mock(ContentStore.class);
        var secondaryStore = mock(ContentStore.class);
        var stores = Map.of("primary", primaryStore, "secondary", secondaryStore);

        var registry = new DefaultContentStoreRegistry("primary", stores);

        var reference = ContentReference.of("secondary", "content-456");
        var result = registry.getStoreForReading(reference);

        assertThat(result).isSameAs(secondaryStore);
    }

    @Test
    void testGetStoreForReadingWithNonExistentStoreId() {
        var store = mock(ContentStore.class);
        var registry = new DefaultContentStoreRegistry("primary", store);

        var reference = ContentReference.of("nonexistent", "content-789");

        assertThatThrownBy(() -> registry.getStoreForReading(reference))
            .isInstanceOf(UnreadableContentException.class)
            .hasMessageContaining("Content store 'nonexistent' not found");
    }

    @Test
    void testGetStoreForReadingWithEmptyStoreId() throws UnreadableContentException {
        var store = mock(ContentStore.class);
        var registry = new DefaultContentStoreRegistry("primary", store);

        // Parse a reference that looks like it has a store ID but it's actually empty
        var reference = ContentReference.parse(":content-123");
        var result = registry.getStoreForReading(reference);

        // Should fall back to write store
        assertThat(result).isSameAs(store);
    }

    @Test
    void testMultipleStoresScenario() throws UnreadableContentException {
        // Simulate a migration scenario with old and new stores
        var oldStore = mock(ContentStore.class);
        var newStore = mock(ContentStore.class);
        var archiveStore = mock(ContentStore.class);

        var stores = Map.of(
            "old", oldStore,
            "new", newStore,
            "archive", archiveStore
        );

        var registry = new DefaultContentStoreRegistry("new", stores);

        // New content should go to the "new" store
        assertThat(registry.getWriteStore()).isSameAs(newStore);
        assertThat(registry.getWriteStoreId()).isEqualTo("new");

        // Old references without store ID should be read from new store (current write store)
        var legacyRef = ContentReference.of("legacy-content-123");
        assertThat(registry.getStoreForReading(legacyRef)).isSameAs(newStore);

        // References with explicit store IDs should use the correct store
        var oldRef = ContentReference.of("old", "old-content-456");
        assertThat(registry.getStoreForReading(oldRef)).isSameAs(oldStore);

        var archiveRef = ContentReference.of("archive", "archived-content-789");
        assertThat(registry.getStoreForReading(archiveRef)).isSameAs(archiveStore);

        var newRef = ContentReference.of("new", "new-content-012");
        assertThat(registry.getStoreForReading(newRef)).isSameAs(newStore);
    }
}
