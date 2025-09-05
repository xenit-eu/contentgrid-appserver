package com.contentgrid.appserver.content.impl.encryption.engine;

import com.contentgrid.appserver.content.api.ContentReader;
import com.contentgrid.appserver.content.api.UnreadableContentException;
import com.contentgrid.appserver.content.api.range.ResolvedContentRange;
import com.contentgrid.appserver.content.impl.encryption.keys.KeyBytes;
import java.io.OutputStream;
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
     * @param ciphertextStream The underlying content stream that will receive encrypted content
     * @param encryptionParameters Parameters for the encryption algorithm
     * @return A stream that receives unencrypted content and will encrypt it before writing it to the ciphertextStream
     */
    OutputStream encrypt(OutputStream ciphertextStream, EncryptionParameters encryptionParameters);

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
