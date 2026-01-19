package com.contentgrid.appserver.contentstore.api;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.NonNull;

/**
 * Default implementation of ContentStoreRegistry
 */
public class DefaultContentStoreRegistry implements ContentStoreRegistry {

    private final Map<String, ContentStore> stores = new ConcurrentHashMap<>();
    private volatile String writeStoreId;
    private volatile ContentStore writeStore;

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
     * Register an additional content store for reading
     * @param storeId The store identifier
     * @param store The content store
     */
    public void registerStore(@NonNull String storeId, @NonNull ContentStore store) {
        stores.put(storeId, store);
    }

    /**
     * Set the active write store
     * @param storeId The identifier of the store to use for writing
     * @throws IllegalArgumentException if the store ID is not registered
     */
    public void setWriteStore(@NonNull String storeId) {
        ContentStore store = stores.get(storeId);
        if (store == null) {
            throw new IllegalArgumentException(
                    "Content store '" + storeId + "' is not registered"
            );
        }
        this.writeStoreId = storeId;
        this.writeStore = store;
    }

    /**
     * Get all registered store IDs
     * @return Set of all store IDs
     */
    public java.util.Set<String> getStoreIds() {
        return java.util.Collections.unmodifiableSet(stores.keySet());
    }
}
