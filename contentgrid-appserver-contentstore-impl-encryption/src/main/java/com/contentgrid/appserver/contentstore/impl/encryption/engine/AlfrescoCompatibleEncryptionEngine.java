package com.contentgrid.appserver.contentstore.impl.encryption.engine;

import com.contentgrid.appserver.contentstore.api.ContentReader;
import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.UnreadableContentException;
import com.contentgrid.appserver.contentstore.api.range.ResolvedContentRange;
import com.contentgrid.appserver.contentstore.impl.encryption.CryptoInitializationFailureException;
import com.contentgrid.appserver.contentstore.impl.utils.SkippableCipherInputStream;

import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

public class AlfrescoCompatibleEncryptionEngine implements ContentEncryptionEngine {

    public static final String ALFRESCO_ALG_PREFIX = "Alfresco-";

    private static final String COMMON_MODE_AND_PADDING = "CBC/PKCS5Padding";

    // Taken from de.acosix.alfresco.simplecontentstores.repo.store.encrypted.CipherUtil
    // See https://github.com/Acosix/alfresco-simple-content-stores
    // RSA mode not supported for file encryption (only symmetric-key algorithms)
    private static final Map<String, String> MODES_AND_PADDINGS_BY_ALGORITHM = Map.of(
            "AES", COMMON_MODE_AND_PADDING,
            "DES", COMMON_MODE_AND_PADDING,
            "DESede", COMMON_MODE_AND_PADDING
    );

    @Override
    public boolean supports(@NonNull DataEncryptionAlgorithm algorithm) {
        boolean supported = true;

        // alfresco-simple-content-stores supports arbitrary algorithms, though only symmetric ones
        // check Java support of algorithm (which may include mode + padding)
        // and also check key algorithm for being symmetric
        String algorithmValue = algorithm.getValue();
        if (algorithmValue.startsWith(ALFRESCO_ALG_PREFIX)) {
            algorithmValue = algorithmValue.substring(ALFRESCO_ALG_PREFIX.length());
            String keyAlgorithmValue = algorithmValue;
            if (keyAlgorithmValue.contains("/")) {
                keyAlgorithmValue = keyAlgorithmValue.substring(0, keyAlgorithmValue.indexOf('/'));
            }
    
            Cipher cipher = null;
            try {
                cipher = Cipher.getInstance(algorithmValue);
            } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
                supported = false;
            }
    
            if (cipher != null) {
                try {
                    KeyGenerator.getInstance(keyAlgorithmValue);
                } catch (NoSuchAlgorithmException e) {
                    supported = false;
                }
            }
        } else {
            supported = false;
        }

        return supported;
    }

    @Override
    public EncryptionParameters createNewParameters() {
        throw new UnsupportedOperationException("Alfresco-compatible encryption engine can only be used for decryption");
    }

    @Override
    public InputStream encrypt(@NonNull InputStream plaintextStream, @NonNull EncryptionParameters encryptionParameters) {
        throw new UnsupportedOperationException("Alfresco-compatible encryption engine can only be used for decryption");
    }

    private Cipher initializeCipher(EncryptionParameters parameters)
            throws CryptoInitializationFailureException {
        String algorithm = parameters.getAlgorithm().getValue();
        if (!algorithm.startsWith(ALFRESCO_ALG_PREFIX)) {
            throw new CryptoInitializationFailureException(new UnsupportedOperationException("Not an Alfresco-compatible encryption algorithm"));
        }
        algorithm = algorithm.substring(ALFRESCO_ALG_PREFIX.length());
        try {
            Cipher cipher = Cipher.getInstance(algorithm);
            Key key = new SecretKey(parameters.getSecretKey(), algorithm);
            int blockSize = cipher.getBlockSize();
            if (blockSize == 0) {
                // init without iv parameters
                cipher.init(Cipher.DECRYPT_MODE, key);
            } else {
                // Same padding logic as in alfresco encrypted storage plugin
                if (MODES_AND_PADDINGS_BY_ALGORITHM.containsKey(algorithm)) {
                    algorithm = algorithm + "/" + MODES_AND_PADDINGS_BY_ALGORITHM.get(algorithm);
                    cipher = Cipher.getInstance(algorithm);
                }
                // Always use zero IV because we will always read from the start.
                // This is fine because this decryption engine is strictly a migration tool.
                cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(new byte[blockSize]));
            }
            return cipher;
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidAlgorithmParameterException |
                 InvalidKeyException e) {
            throw new CryptoInitializationFailureException(e);
        } finally {
            // After cipher init, the cipher should manage its own copy of the key
            // So the encryption parameters can be destroyed now
            parameters.destroy();
        }

    }

    @Override
    public ContentReader decrypt(
            @NonNull CiphertextReaderSupplier cipherTextReaderSupplier,
            @NonNull EncryptionParameters encryptionParameters,
            ResolvedContentRange contentRange
    ) throws UnreadableContentException, CryptoInitializationFailureException {
        Cipher cipher = initializeCipher(encryptionParameters);
        // Always read full content, then skip part of the decrypted content if a range was requested.
        // This is fine because the Alfresco decryption engine is strictly for migrating data
        var rawReader = cipherTextReaderSupplier.getReader(null);
        return new DecryptingContentReader(rawReader, cipher);
    }

    @RequiredArgsConstructor
    private static class DecryptingContentReader implements ContentReader {

        private final ContentReader delegate;
        private final Cipher cipher;

        @Override
        public InputStream getContentInputStream() throws UnreadableContentException {
            InputStream raw = delegate.getContentInputStream();
            // CipherInputStream does not skip(n) into not-yet-decrypted data
            // Spring StreamUtils.copyRange needs that behaviour
            // so we use our SkippableCipherInputStream
            return new SkippableCipherInputStream(raw, cipher);
        }

        @Override
        public ContentReference getReference() {
            return delegate.getReference();
        }

        @Override
        public String getDescription() {
            return "Decrypted %s".formatted(delegate.getDescription());
        }
    }
}
