package com.contentgrid.appserver.contentstore.impl.encryption.testing;

import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.DataEncryptionKeyAccessor;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.StoredDataEncryptionKey;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.WrappingKeyId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class InMemoryDataEncryptionKeyAccessor implements DataEncryptionKeyAccessor {
    private final Map<ContentReference, List<StoredDataEncryptionKey>> storage = new HashMap<>();

    private List<StoredDataEncryptionKey> access(ContentReference contentReference) {
        return storage.computeIfAbsent(contentReference, unused -> new ArrayList<>());
    }

    @Override
    public List<StoredDataEncryptionKey> findAllKeys(ContentReference contentReference) {
        return createCopy(access(contentReference));
    }

    @Override
    public void addKeys(ContentReference contentReference, Set<StoredDataEncryptionKey> dataEncryptionKeys) {
        access(contentReference).addAll(createCopy(dataEncryptionKeys));
    }

    @Override
    public void removeKey(ContentReference contentReference, WrappingKeyId wrappingKeyId) {
        access(contentReference)
                .removeIf(storedKey -> Objects.equals(storedKey.getWrappingKeyId(), wrappingKeyId));
    }

    @Override
    public void clearKeys(ContentReference contentReference) {
        storage.remove(contentReference);
    }

    private static StoredDataEncryptionKey createCopy(StoredDataEncryptionKey storedDataEncryptionKey) {
        return new StoredDataEncryptionKey(
                storedDataEncryptionKey.getDataEncryptionAlgorithm(),
                storedDataEncryptionKey.getWrappingKeyId(),
                storedDataEncryptionKey.getEncryptedKeyData().clone(),
                storedDataEncryptionKey.getInitializationVector()
        );
    }

    private List<StoredDataEncryptionKey> createCopy(Collection<StoredDataEncryptionKey> dataEncryptionKeys) {
        return dataEncryptionKeys.stream()
                .map(InMemoryDataEncryptionKeyAccessor::createCopy)
                .toList();
    }
}
