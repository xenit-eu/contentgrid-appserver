package com.contentgrid.appserver.contentstore.impl.encryption.keys;

import com.contentgrid.appserver.contentstore.impl.encryption.engine.DataEncryptionAlgorithm;
import javax.security.auth.Destroyable;
import lombok.Value;

/**
 * Representation of the stored data encryption key
 */
@Value
public class StoredDataEncryptionKey implements Destroyable {
    /**
     * The encryption algorithm used for data encryption
     */
    DataEncryptionAlgorithm dataEncryptionAlgorithm;
    /**
     * The identifier for the wrapping key that was used for key encryption
     * Set to {@link WrappingKeyId#unwrapped()} for keys that have not been wrapped
     */
    WrappingKeyId wrappingKeyId;

    /**
     * The encrypted data encryption key
     */
    KeyBytes encryptedKeyData;

    /**
     * The IV for the encryption algorithm
     */
    byte[] initializationVector;

    @Override
    public boolean isDestroyed() {
        return encryptedKeyData.isDestroyed();
    }

    @Override
    public void destroy() {
        encryptedKeyData.destroy();
    }
}
