package com.contentgrid.appserver.contentstore.impl.encryption;

import lombok.NonNull;

/**
 * Exception thrown by an {@link com.contentgrid.appserver.contentstore.impl.encryption.engine.ContentEncryptionEngine} when
 * encryption or decryption fails early during initialization
 */
public class CryptoInitializationFailureException extends Exception {
    public CryptoInitializationFailureException(@NonNull Exception cause) {
        super(cause);
    }
}
