package com.contentgrid.appserver.contentstore.api;

import java.util.Optional;
import lombok.NonNull;

/**
 * Registry for managing multiple content stores.
 * <p>
 * Allows using multiple content stores simultaneously, with one designated as the active write store
 * and others available for reading existing content.
 */
public interface ContentStoreRegistry {

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
    default ContentStore getStoreForReading(@NonNull ContentReference contentReference)
            throws UnreadableContentException {
        String storeId = contentReference.getStoreId();
        if (storeId == null || storeId.isEmpty()) {
            // For backward compatibility, use write store if no store ID is specified
            return getWriteStore();
        }
        return getStore(storeId)
                .orElseThrow(() -> new UnreadableContentException(
                        contentReference,
                        "Content store '" + storeId + "' not found"
                ));
    }
}
