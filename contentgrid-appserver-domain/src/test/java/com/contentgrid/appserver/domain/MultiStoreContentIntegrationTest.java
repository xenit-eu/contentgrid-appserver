package com.contentgrid.appserver.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.contentgrid.appserver.contentstore.api.ContentAccessor;
import com.contentgrid.appserver.contentstore.api.ContentReader;
import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.contentstore.api.ContentStoreRegistry;
import com.contentgrid.appserver.contentstore.api.DefaultContentStoreRegistry;
import com.contentgrid.appserver.contentstore.api.UnreadableContentException;
import com.contentgrid.appserver.contentstore.api.range.ResolvedContentRange;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Integration test demonstrating multi-store content functionality.
 *
 * This test shows how multiple content stores can be used simultaneously,
 * with one active for writing and others available for reading.
 */
class MultiStoreContentIntegrationTest {

    private ContentStore primaryStore;
    private ContentStore legacyStore;
    private ContentStore archiveStore;
    private ContentStoreRegistry registry;

    @BeforeEach
    void setUp() {
        primaryStore = mock(ContentStore.class);
        legacyStore = mock(ContentStore.class);
        archiveStore = mock(ContentStore.class);

        var stores = Map.of(
            "primary",
            primaryStore,
            "legacy",
            legacyStore,
            "archive",
            archiveStore
        );

        registry = new DefaultContentStoreRegistry("primary", stores);
    }

    @Test
    void testNewContentIsWrittenToActiveStore() throws Exception {
        // Arrange
        var content = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        var inputStream = new ByteArrayInputStream(content);

        var mockAccessor = mock(ContentAccessor.class);
        when(mockAccessor.getReference()).thenReturn(
            ContentReference.of("content-123")
        );
        when(mockAccessor.getContentSize()).thenReturn((long) content.length);
        when(primaryStore.writeContent(any(InputStream.class))).thenReturn(
            mockAccessor
        );

        // Act - Write content
        var accessor = registry.getWriteStore().writeContent(inputStream);
        var reference = ContentReference.of(
            registry.getWriteStoreId(),
            accessor.getReference().getValue()
        );

        // Assert
        verify(primaryStore).writeContent(any(InputStream.class));
        assertThat(reference.getStoreId()).isEqualTo("primary");
        assertThat(reference.getValue()).isEqualTo("content-123");
        assertThat(reference.toStorageFormat()).isEqualTo(
            "primary:content-123"
        );
    }

    @Test
    void testReadingContentFromCorrectStore() throws Exception {
        // Arrange - Content in primary store
        var primaryReference = ContentReference.parse("primary:content-abc");
        var primaryReader = mock(ContentReader.class);
        var primaryContent = new ByteArrayInputStream(
            "Primary content".getBytes()
        );
        when(primaryReader.getContentInputStream()).thenReturn(primaryContent);
        when(
            primaryStore.getReader(
                eq(primaryReference),
                any(ResolvedContentRange.class)
            )
        ).thenReturn(primaryReader);

        // Arrange - Content in legacy store
        var legacyReference = ContentReference.parse("legacy:content-xyz");
        var legacyReader = mock(ContentReader.class);
        var legacyContent = new ByteArrayInputStream(
            "Legacy content".getBytes()
        );
        when(legacyReader.getContentInputStream()).thenReturn(legacyContent);
        when(
            legacyStore.getReader(
                eq(legacyReference),
                any(ResolvedContentRange.class)
            )
        ).thenReturn(legacyReader);

        // Act & Assert - Read from primary store
        var primaryStore = registry.getStoreForReading(primaryReference);
        assertThat(primaryStore).isSameAs(this.primaryStore);

        // Act & Assert - Read from legacy store
        var legacyStore = registry.getStoreForReading(legacyReference);
        assertThat(legacyStore).isSameAs(this.legacyStore);
    }

    @Test
    void testBackwardCompatibilityWithReferencesWithoutStoreId()
        throws Exception {
        // Arrange - Old reference without store ID
        var legacyReference = ContentReference.parse("old-content-456");

        var reader = mock(ContentReader.class);
        var content = new ByteArrayInputStream("Old content".getBytes());
        when(reader.getContentInputStream()).thenReturn(content);
        when(
            primaryStore.getReader(
                eq(legacyReference),
                any(ResolvedContentRange.class)
            )
        ).thenReturn(reader);

        // Act - Read old reference (should use primary/write store)
        var store = registry.getStoreForReading(legacyReference);

        // Assert - Falls back to write store for backward compatibility
        assertThat(store).isSameAs(primaryStore);
        assertThat(legacyReference.getStoreId()).isNull();
    }

    @Test
    void testMigrationScenario() throws Exception {
        // Simulate a migration from legacy to primary store

        // 1. Old content exists in legacy store
        var oldReference = ContentReference.of("legacy", "migrate-me-123");
        var oldReader = mock(ContentReader.class);
        var oldContent = new ByteArrayInputStream(
            "Content to migrate".getBytes()
        );
        when(oldReader.getContentInputStream()).thenReturn(oldContent);
        when(
            legacyStore.getReader(
                eq(oldReference),
                any(ResolvedContentRange.class)
            )
        ).thenReturn(oldReader);

        // 2. Read from legacy store
        var legacyStoreInstance = registry.getStoreForReading(oldReference);
        assertThat(legacyStoreInstance).isSameAs(legacyStore);

        var reader = legacyStoreInstance.getReader(
            oldReference,
            ResolvedContentRange.fullRange(18)
        );
        var contentStream = reader.getContentInputStream();

        // 3. Write to primary store (migration)
        var newAccessor = mock(ContentAccessor.class);
        when(newAccessor.getReference()).thenReturn(
            ContentReference.of("migrated-content-123")
        );
        when(newAccessor.getContentSize()).thenReturn(18L);
        when(primaryStore.writeContent(any(InputStream.class))).thenReturn(
            newAccessor
        );

        var migratedAccessor = registry
            .getWriteStore()
            .writeContent(contentStream);
        var newReference = ContentReference.of(
            registry.getWriteStoreId(),
            migratedAccessor.getReference().getValue()
        );

        // 4. Verify new reference points to primary store
        assertThat(newReference.getStoreId()).isEqualTo("primary");
        assertThat(newReference.getValue()).isEqualTo("migrated-content-123");

        // 5. New reference should read from primary store
        var newStore = registry.getStoreForReading(newReference);
        assertThat(newStore).isSameAs(primaryStore);
    }

    @Test
    void testSwitchingActiveWriteStore() throws Exception {
        // Initially, primary is the write store
        assertThat(registry.getWriteStoreId()).isEqualTo("primary");
        assertThat(registry.getWriteStore()).isSameAs(primaryStore);

        // Switch to archive as write store
        ((DefaultContentStoreRegistry) registry).setWriteStore("archive");

        // Verify switch
        assertThat(registry.getWriteStoreId()).isEqualTo("archive");
        assertThat(registry.getWriteStore()).isSameAs(archiveStore);

        // New content should go to archive store
        var mockAccessor = mock(ContentAccessor.class);
        when(mockAccessor.getReference()).thenReturn(
            ContentReference.of("archived-123")
        );
        when(mockAccessor.getContentSize()).thenReturn(100L);
        when(archiveStore.writeContent(any(InputStream.class))).thenReturn(
            mockAccessor
        );

        var content = new ByteArrayInputStream("Archive me".getBytes());
        registry.getWriteStore().writeContent(content);

        verify(archiveStore).writeContent(any(InputStream.class));
    }

    @Test
    void testDynamicStoreRegistration() {
        // Add a new store dynamically
        var newStore = mock(ContentStore.class);
        ((DefaultContentStoreRegistry) registry).registerStore(
            "new-store",
            newStore
        );

        // Verify it's registered
        assertThat(registry.getStore("new-store")).contains(newStore);
        assertThat(
            ((DefaultContentStoreRegistry) registry).getStoreIds()
        ).containsExactlyInAnyOrder(
            "primary",
            "legacy",
            "archive",
            "new-store"
        );
    }

    @Test
    void testContentReferenceRoundTrip() {
        // Create reference with store ID
        var original = ContentReference.of("primary", "content-789");

        // Convert to storage format
        var stored = original.toStorageFormat();
        assertThat(stored).isEqualTo("primary:content-789");

        // Parse back
        var parsed = ContentReference.parse(stored);

        // Verify equality
        assertThat(parsed).isEqualTo(original);
        assertThat(parsed.getStoreId()).isEqualTo("primary");
        assertThat(parsed.getValue()).isEqualTo("content-789");
    }

    @Test
    void testMultiStoreReadDistribution() throws Exception {
        // Simulate content distributed across multiple stores

        // Content in primary store
        var ref1 = ContentReference.parse("primary:content-1");
        var reader1 = mockReader("Content 1");
        when(primaryStore.getReader(eq(ref1), any())).thenReturn(reader1);

        // Content in legacy store
        var ref2 = ContentReference.parse("legacy:content-2");
        var reader2 = mockReader("Content 2");
        when(legacyStore.getReader(eq(ref2), any())).thenReturn(reader2);

        // Content in archive store
        var ref3 = ContentReference.parse("archive:content-3");
        var reader3 = mockReader("Content 3");
        when(archiveStore.getReader(eq(ref3), any())).thenReturn(reader3);

        // Read from each store
        var store1 = registry.getStoreForReading(ref1);
        var store2 = registry.getStoreForReading(ref2);
        var store3 = registry.getStoreForReading(ref3);

        // Verify correct stores are used
        assertThat(store1).isSameAs(primaryStore);
        assertThat(store2).isSameAs(legacyStore);
        assertThat(store3).isSameAs(archiveStore);
    }

    @Test
    void testInvalidStoreIdThrowsException() {
        var invalidReference = ContentReference.parse(
            "nonexistent:content-123"
        );

        assertThat(invalidReference.getStoreId()).isEqualTo("nonexistent");

        // Should throw exception when trying to read from non-existent store
        org.junit.jupiter.api.Assertions.assertThrows(
            UnreadableContentException.class,
            () -> registry.getStoreForReading(invalidReference)
        );
    }

    @Test
    void testStoreIsolation() throws Exception {
        // Ensure stores are isolated - writes to one don't affect others

        var content = new ByteArrayInputStream("Test content".getBytes());
        var accessor = mock(ContentAccessor.class);
        when(accessor.getReference()).thenReturn(
            ContentReference.of("test-123")
        );
        when(accessor.getContentSize()).thenReturn(12L);
        when(primaryStore.writeContent(any(InputStream.class))).thenReturn(
            accessor
        );

        // Write to primary store
        registry.getWriteStore().writeContent(content);

        // Verify only primary store was called
        verify(primaryStore).writeContent(any(InputStream.class));
        verify(legacyStore, org.mockito.Mockito.never()).writeContent(
            any(InputStream.class)
        );
        verify(archiveStore, org.mockito.Mockito.never()).writeContent(
            any(InputStream.class)
        );
    }

    private ContentReader mockReader(String content) throws IOException {
        var reader = mock(ContentReader.class);
        var stream = new ByteArrayInputStream(
            content.getBytes(StandardCharsets.UTF_8)
        );
        when(reader.getContentInputStream()).thenReturn(stream);
        return reader;
    }
}
