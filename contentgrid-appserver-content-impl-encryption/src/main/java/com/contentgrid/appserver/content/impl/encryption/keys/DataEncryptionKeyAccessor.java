package com.contentgrid.appserver.content.impl.encryption.keys;

import com.contentgrid.appserver.content.api.ContentReference;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reads and writes data encryption keys for a content object
 */
public interface DataEncryptionKeyAccessor {

    List<StoredDataEncryptionKey> findAllKeys(ContentReference contentReference);
    default List<StoredDataEncryptionKey> findKeys(ContentReference contentReference, Collection<WrappingKeyId> wrappingKeyIds) {
        return findAllKeys(contentReference);
    }

    default void addKey(ContentReference contentReference, StoredDataEncryptionKey dataEncryptionKey) {
        addKeys(contentReference, Set.of(dataEncryptionKey));
    }
    void addKeys(ContentReference contentReference, Set<StoredDataEncryptionKey> dataEncryptionKeys);
    void removeKey(ContentReference contentReference, WrappingKeyId wrappingKeyId);
    void clearKeys(ContentReference contentReference);
}
