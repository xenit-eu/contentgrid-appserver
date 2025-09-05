package com.contentgrid.appserver.content.impl.encryption.keys;


import com.contentgrid.appserver.content.impl.encryption.engine.ContentEncryptionEngine.EncryptionParameters;
import java.util.Set;

/**
 * Encryption and decryption of data encryption keys
 */
public interface DataEncryptionKeyWrapper {

    /**
     * @return The key IDs that are supported by this wrapper
     */
    Set<WrappingKeyId> getSupportedKeyIds();

    /**
     * @return Whether this key wrapper can decrypt keys
     */
    boolean canDecrypt();

    /**
     * @return Whether this key wrapper can encrypt keys
     */
    boolean canEncrypt();

    /**
     * Encrypt the data encryption key
     * @param dataEncryptionParameters Unencrypted data encryption parameters
     * @return A representation of the encrypted data encryption key
     */
    StoredDataEncryptionKey wrapEncryptionKey(EncryptionParameters dataEncryptionParameters);

    /**
     * Decrypt the encrypted data encryption key
     * @param encryptedDataEncryptionKey A representation of the encrypted data encryption key
     * @return Unencrypted data encryption parameters
     */
    EncryptionParameters unwrapEncryptionKey(StoredDataEncryptionKey encryptedDataEncryptionKey);

}
