package com.contentgrid.appserver.contentstore.api;

import com.contentgrid.appserver.contentstore.api.range.ResolvedContentRange;
import java.io.InputStream;
import java.util.Optional;
import lombok.NonNull;

/**
 * Registry for managing multiple content stores, acting as a unified ContentStore facade.
 * <p>
 * This registry implements ContentStore and delegates operations to the appropriate underlying stores:
 * <ul>
 *   <li>Write operations (writeContent) are delegated to the active write store</li>
 *   <li>Read operations (getReader) are routed based on the store ID in the content reference</li>
 *   <li>Delete operations (remove) are delegated to the store specified in the content reference</li>
 * </ul>
 * <p>
 * The registry allows using multiple content stores simultaneously, with one designated as the active write store
 * and others available for reading existing content. This enables scenarios like:
 * <ul>
 *   <li>Zero-downtime migration between storage systems</li>
 *   <li>Tiered storage with different stores for hot/cold data</li>
 *   <li>Gradual migration from legacy storage to new storage</li>
 * </ul>
 */
public interface ContentStoreRegistry extends ContentStore {
    /**
     * Get a content store by its identifier
     * @param storeId The store identifier
     * @return The content store, or empty if not found
     */
    Optional<ContentStore> getStore(@NonNull String storeId);

    /**
     * Get the active content store for writing new content
     * @return The active write store
     */
    @NonNull
    ContentStore getWriteStore();

    /**
     * Get the identifier of the active write store
     * @return The write store identifier
     */
    @NonNull
    String getWriteStoreId();

    /**
     * Get a content store for reading the given content reference.
     * <p>
     * If the reference includes a store ID, that store is returned.
     * Otherwise, returns the write store (for backward compatibility with references
     * that don't have a store ID).
     *
     * @param contentReference The content reference
     * @return The content store to use for reading
     * @throws UnreadableContentException if the referenced store is not found
     */
    @NonNull
    default ContentStore getStoreForReading(
        @NonNull ContentReference contentReference
    ) throws UnreadableContentException {
        String storeId = contentReference.getStoreId();
        if (storeId == null || storeId.isEmpty()) {
            // For backward compatibility, use write store if no store ID is specified
            return getWriteStore();
        }
        return getStore(storeId).orElseThrow(() ->
            new UnreadableContentException(
                contentReference,
                "Content store '" + storeId + "' not found"
            )
        );
    }

    /**
     * Default implementation of getReader that routes to the appropriate store based on content reference.
     * <p>
     * This method extracts the store ID from the content reference and delegates to the appropriate store.
     * If no store ID is present, it uses the write store for backward compatibility.
     */
    @Override
    default ContentReader getReader(
        ContentReference contentReference,
        ResolvedContentRange contentRange
    ) throws UnreadableContentException {
        ContentStore store = getStoreForReading(contentReference);
        return store.getReader(contentReference, contentRange);
    }

    /**
     * Default implementation of writeContent that delegates to the active write store.
     * <p>
     * The returned ContentAccessor will have a reference that includes the write store ID.
     */
    @Override
    default ContentAccessor writeContent(InputStream inputStream)
        throws UnwritableContentException {
        return getWriteStore().writeContent(inputStream);
    }

    /**
     * Default implementation of remove that routes to the appropriate store based on content reference.
     * <p>
     * This method extracts the store ID from the content reference and delegates to the appropriate store.
     * If no store ID is present, it uses the write store for backward compatibility.
     */
    @Override
    default void remove(ContentReference contentReference)
        throws UnwritableContentException {
        try {
            ContentStore store = getStoreForReading(contentReference);
            store.remove(contentReference);
        } catch (UnreadableContentException e) {
            // Convert to UnwritableContentException for consistency
            throw new UnwritableContentException(contentReference, e);
        }
    }
}
