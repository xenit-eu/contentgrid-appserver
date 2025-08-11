package com.contentgrid.appserver.content.impl.encryption;

import com.contentgrid.appserver.content.api.ContentReference;
import com.contentgrid.appserver.content.impl.encryption.keys.WrappingKeyId;
import java.util.Set;
import lombok.Getter;
import lombok.NonNull;

@Getter
public class NoDecryptableDataEncryptionKeysException extends UndecryptableContentException {
    @NonNull
    private final Set<WrappingKeyId> storedKeys;
    @NonNull
    private final Set<WrappingKeyId> decryptableKeys;

    public NoDecryptableDataEncryptionKeysException(
            @NonNull ContentReference reference,
            @NonNull Set<WrappingKeyId> storedKeys,
            @NonNull Set<WrappingKeyId> decryptableKeys
    ) {
        super(reference, "No wrappers can decrypt data encryption keys %s: Wrappers only support %s".formatted(storedKeys, decryptableKeys));
        this.storedKeys = Set.copyOf(storedKeys);
        this.decryptableKeys = Set.copyOf(decryptableKeys);
    }

}
