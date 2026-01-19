package com.contentgrid.appserver.contentstore.api;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.NonNull;

/**
 * Default implementation of ContentStoreRegistry
 */
public class DefaultContentStoreRegistry implements ContentStoreRegistry {

    private final Map<String, ContentStore> stores = new ConcurrentHashMap<>();
    private final String writeStoreId;
    private final ContentStore writeStore;

    /**
     * Create a registry with a single content store as the write store
     * @param writeStoreId The identifier for the write store
     * @param writeStore The content store to use for writing
     */
    public DefaultContentStoreRegistry(
        @NonNull String writeStoreId,
        @NonNull ContentStore writeStore
    ) {
        this.writeStoreId = writeStoreId;
        this.writeStore = writeStore;
        this.stores.put(writeStoreId, writeStore);
    }

    /**
     * Create a registry with multiple content stores
     * @param writeStoreId The identifier for the active write store
     * @param stores Map of store IDs to content stores
     * @throws IllegalArgumentException if writeStoreId is not in the stores map
     */
    public DefaultContentStoreRegistry(
        @NonNull String writeStoreId,
        @NonNull Map<String, ContentStore> stores
    ) {
        if (!stores.containsKey(writeStoreId)) {
            throw new IllegalArgumentException(
                "Write store ID '" + writeStoreId + "' not found in stores map"
            );
        }
        this.writeStoreId = writeStoreId;
        this.writeStore = stores.get(writeStoreId);
        this.stores.putAll(stores);
    }

    @Override
    public Optional<ContentStore> getStore(@NonNull String storeId) {
        return Optional.ofNullable(stores.get(storeId));
    }

    @Override
    @NonNull
    public ContentStore getWriteStore() {
        return writeStore;
    }

    @Override
    @NonNull
    public String getWriteStoreId() {
        return writeStoreId;
    }


    /**
     * Get all registered store IDs
     * @return Set of all store IDs
     */
    public java.util.Set<String> getStoreIds() {
        return java.util.Collections.unmodifiableSet(stores.keySet());
    }

    /**
     * Override writeContent to wrap the returned ContentAccessor with a reference
     * that includes the write store ID.
     */
    @Override
    public ContentAccessor writeContent(InputStream inputStream)
        throws UnwritableContentException {
        ContentAccessor originalAccessor = writeStore.writeContent(inputStream);

        // Wrap the reference to include the store ID
        ContentReference referenceWithStoreId = ContentReference.of(
            writeStoreId,
            originalAccessor.getReference().getValue()
        );

        return new ContentAccessorWithStoreId(
            originalAccessor,
            referenceWithStoreId
        );
    }

    /**
     * Wrapper for ContentAccessor that overrides the reference to include store ID
     */
    private static class ContentAccessorWithStoreId implements ContentAccessor {

        private final ContentAccessor delegate;
        private final ContentReference referenceWithStoreId;

        ContentAccessorWithStoreId(
            ContentAccessor delegate,
            ContentReference referenceWithStoreId
        ) {
            this.delegate = delegate;
            this.referenceWithStoreId = referenceWithStoreId;
        }

        @Override
        public ContentReference getReference() {
            return referenceWithStoreId;
        }

        @Override
        public long getContentSize() {
            return delegate.getContentSize();
        }

        @Override
        public String getDescription() {
            return delegate.getDescription();
        }
    }
}
