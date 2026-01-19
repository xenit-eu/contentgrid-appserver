package com.contentgrid.appserver.contentstore.api;

import java.io.Serializable;
import java.util.Objects;
import lombok.NonNull;
import lombok.Value;

/**
 * Reference to a content object, optionally associated with a specific content store
 */
@Value
public class ContentReference implements Serializable {

    @NonNull
    String value;

    String storeId;

    private ContentReference(@NonNull String value, String storeId) {
        this.value = Objects.requireNonNull(value, "value");
        this.storeId = storeId;
    }

    /**
     * Create a content reference without a store ID (for backward compatibility)
     * @param value The content identifier
     * @return A content reference
     */
    public static ContentReference of(@NonNull String value) {
        return new ContentReference(value, null);
    }

    /**
     * Create a content reference with a store ID
     * @param storeId The store identifier
     * @param value The content identifier
     * @return A content reference
     */
    public static ContentReference of(
        @NonNull String storeId,
        @NonNull String value
    ) {
        return new ContentReference(
            value,
            Objects.requireNonNull(storeId, "storeId")
        );
    }

    /**
     * Parse a content reference from storage format.
     * Format can be either "value" or "storeId:value"
     * @param storedValue The stored reference string
     * @return A parsed content reference
     */
    public static ContentReference parse(@NonNull String storedValue) {
        Objects.requireNonNull(storedValue, "storedValue");
        int separatorIndex = storedValue.indexOf(':');
        if (separatorIndex >= 0) {
            String storeId = storedValue.substring(0, separatorIndex);
            String value = storedValue.substring(separatorIndex + 1);
            // Treat empty store ID as null for consistency
            if (storeId.isEmpty()) {
                storeId = null;
            }
            return new ContentReference(value, storeId);
        }
        return new ContentReference(storedValue, null);
    }

    /**
     * Get the storage format of this reference.
     * Returns "storeId:value" if storeId is present, otherwise just "value"
     * @return The storage format string
     */
    public String toStorageFormat() {
        if (storeId != null && !storeId.isEmpty()) {
            return storeId + ":" + value;
        }
        return value;
    }

}
