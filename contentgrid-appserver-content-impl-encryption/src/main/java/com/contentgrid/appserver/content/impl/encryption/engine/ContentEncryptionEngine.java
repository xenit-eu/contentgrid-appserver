package com.contentgrid.appserver.content.impl.encryption.engine;

import com.contentgrid.appserver.content.api.ContentReader;
import com.contentgrid.appserver.content.api.UnreadableContentException;
import com.contentgrid.appserver.content.api.range.ResolvedContentRange;
import com.contentgrid.appserver.content.impl.encryption.keys.KeyBytes;
import java.io.InputStream;
import javax.security.auth.Destroyable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Encrypts and decrypts content streams
 */
public interface ContentEncryptionEngine {

    /**
     * Checks if this engine supports the encryption algorithm
     */
    boolean supports(DataEncryptionAlgorithm algorithm);

    /**
     * Creates a new set of encryption parameters for encrypting
     * @return A new set of encryption parameters
     */
    EncryptionParameters createNewParameters();

    /**
     * Encrypt a content stream
     *
     * @param plaintextStream The input stream containing unencrypted content
     * @param encryptionParameters Parameters for the encryption algorithm
     * @return A stream that contains the encrypted content
     */
    InputStream encrypt(InputStream plaintextStream, EncryptionParameters encryptionParameters);

    /**
     * Decrypt an encrypted content stream
     * @param cipherTextReaderSupplier Function to obtain (part of) the encrypted content stream
     * @param encryptionParameters Parameters for the encryption algorithm
     * @param contentRange Parameters for working on a part of the content stream
     * @return A stream of unencrypted content
     */
    ContentReader decrypt(
            CiphertextReaderSupplier cipherTextReaderSupplier,
            EncryptionParameters encryptionParameters,
            ResolvedContentRange contentRange
    ) throws UnreadableContentException;

    /**
     * Content-specific parameters for the encryption algorithm
     */
    @Getter
    @RequiredArgsConstructor
    class EncryptionParameters implements Destroyable {
        private final DataEncryptionAlgorithm algorithm;
        private final KeyBytes secretKey;
        private final byte[] initializationVector;

        @Override
        public boolean isDestroyed() {
            return secretKey.isDestroyed();
        }

        @Override
        public void destroy() {
            secretKey.destroy();
        }
    }

    interface CiphertextReaderSupplier {
        ContentReader getReader(ResolvedContentRange range) throws UnreadableContentException;
    }

}
