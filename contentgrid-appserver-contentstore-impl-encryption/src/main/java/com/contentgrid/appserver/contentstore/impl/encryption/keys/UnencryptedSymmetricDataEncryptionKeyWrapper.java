package com.contentgrid.appserver.contentstore.impl.encryption.keys;

import com.contentgrid.appserver.contentstore.impl.encryption.engine.ContentEncryptionEngine.EncryptionParameters;
import java.util.Set;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UnencryptedSymmetricDataEncryptionKeyWrapper implements DataEncryptionKeyWrapper {
    private final boolean canEncrypt;

    @Override
    public Set<WrappingKeyId> getSupportedKeyIds() {
        return Set.of(WrappingKeyId.unwrapped());
    }

    @Override
    public boolean canDecrypt() {
        return true;
    }

    @Override
    public EncryptionParameters unwrapEncryptionKey(StoredDataEncryptionKey storedKey) {
        return new EncryptionParameters(
                storedKey.getDataEncryptionAlgorithm(),
                // Lifetime of the EncryptionParameters should be separate from StoredDataEncryptionKey
                storedKey.getEncryptedKeyData().clone(),
                storedKey.getInitializationVector()
        );
    }

    @Override
    public boolean canEncrypt() {
        return canEncrypt;
    }

    @Override
    public StoredDataEncryptionKey wrapEncryptionKey(EncryptionParameters dataEncryptionParameters) {
        return new StoredDataEncryptionKey(
                dataEncryptionParameters.getAlgorithm(),
                WrappingKeyId.unwrapped(),
                // Lifetime of the StoredDataEncryptionKey should be separate from EncryptionParameters
                dataEncryptionParameters.getSecretKey().clone(),
                dataEncryptionParameters.getInitializationVector()
        );
    }

}
