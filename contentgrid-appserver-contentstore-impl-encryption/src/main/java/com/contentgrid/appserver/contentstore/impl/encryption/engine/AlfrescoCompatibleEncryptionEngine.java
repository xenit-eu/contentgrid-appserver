package com.contentgrid.appserver.contentstore.impl.encryption.engine;

import com.contentgrid.appserver.contentstore.api.ContentReader;
import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.UnreadableContentException;
import com.contentgrid.appserver.contentstore.api.range.ResolvedContentRange;
import com.contentgrid.appserver.contentstore.impl.encryption.UndecryptableContentException;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.KeyBytes;
import com.contentgrid.appserver.contentstore.impl.utils.SkippingInputStream;
import com.contentgrid.appserver.contentstore.impl.utils.ZeroPrefixedInputStream;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;


import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.DESKeySpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.Destroyable;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class AlfrescoCompatibleEncryptionEngine implements ContentEncryptionEngine {
    // Taken from de.acosix.alfresco.simplecontentstores.repo.store.encrypted.CipherUtil
    // See https://github.com/Acosix/alfresco-simple-content-stores
    private static final Map<String, String> PADDINGS_BY_ALGORITHM = Map.of(
            "AES", "CBC/PKCS5Padding",
            "DES", "CBC/PKCS5Padding",
            "DESede", "CBC/PKCS5Padding",
            "RSA", "ECB/OAEPWithSHA-256AndMGF1Padding"
    );

    public AlfrescoCompatibleEncryptionEngine() {

    }

    @Override
    public boolean supports(DataEncryptionAlgorithm algorithm) {
        return PADDINGS_BY_ALGORITHM.containsKey(algorithm.getValue());
    }

    @Override
    public EncryptionParameters createNewParameters() {
        throw new UnsupportedOperationException("Alfresco-compatible encryption engine can only be used for decryption");
    }

    @Override
    public InputStream encrypt(InputStream plaintextStream, EncryptionParameters encryptionParameters) {
        throw new UnsupportedOperationException("Alfresco-compatible encryption engine can only be used for decryption");
    }

    @RequiredArgsConstructor
    private static class SecretKey implements javax.crypto.SecretKey {
        @Delegate(types = Destroyable.class)
        private final KeyBytes keyBytes;

        private final String algorithm;

        @Override
        public String getAlgorithm() {
            return this.algorithm;
        }

        @Override
        public String getFormat() {
            return "RAW";
        }

        @Override
        public byte[] getEncoded() {
            // This one needs to be a copy, because the AES engine clears it.
            // We don't want to have it destroy our KeyBytes copy
            return keyBytes.getKeyBytesCopy();
        }
    }

    private Cipher initializeCipher(EncryptionParameters parameters)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException {
        String algorithm = parameters.getAlgorithm().getValue();
        Cipher cipher = Cipher.getInstance(algorithm);
        try {
            Key key = new SecretKey(parameters.getSecretKey(), algorithm);
            int blockSize = cipher.getBlockSize();
            if (blockSize == 0) {
                // init without iv parameters
                cipher.init(Cipher.DECRYPT_MODE, key);
            } else {
                // Same padding logic as in alfresco encrypted storage plugin
                if (PADDINGS_BY_ALGORITHM.containsKey(algorithm)) {
                    algorithm = algorithm + "/" + PADDINGS_BY_ALGORITHM.get(algorithm);
                }
                cipher = Cipher.getInstance(algorithm);
                // Always use zero IV because we will always read from the start.
                // This is fine because this decryption engine is strictly a migration tool.
                cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(new byte[blockSize]));
            }
        } finally {
            // After cipher init, the cipher should manage its own copy of the key
            // So the encryption parameters can be destroyed now
            parameters.destroy();
        }

        return cipher;
    }

    @Override
    public ContentReader decrypt(CiphertextReaderSupplier cipherTextReaderSupplier, EncryptionParameters encryptionParameters, ResolvedContentRange contentRange) throws UnreadableContentException {
        // Always read full content, then skip part of the decrypted content if a range was requested.
        // This is fine because the Alfresco decryption engine is strictly for migrating data
        ResolvedContentRange fullRange = ResolvedContentRange.fullRange(contentRange.getContentSize());
        ContentReader rawReader = cipherTextReaderSupplier.getReader(fullRange);
        Cipher cipher;
        try {
            cipher = initializeCipher(encryptionParameters);
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidAlgorithmParameterException |
                 InvalidKeyException e) {
            throw new UndecryptableContentException(rawReader.getReference(), e);
        }

        return new DecryptingContentReader(rawReader, cipher, contentRange.getStartByte());
    }

    @RequiredArgsConstructor
    private static class DecryptingContentReader implements ContentReader {

        private final ContentReader delegate;
        private final Cipher cipher;
        private final long byteStartOffset;

        @Override
        public InputStream getContentInputStream() throws UnreadableContentException {
            InputStream raw = delegate.getContentInputStream();
            InputStream decryptedStream = new CipherInputStream(raw, cipher);
            return new SkippingInputStream(decryptedStream, byteStartOffset);
        }

        @Override
        public ContentReference getReference() {
            return delegate.getReference();
        }

        @Override
        public long getContentSize() {
            return delegate.getContentSize();
        }

        @Override
        public String getDescription() {
            return "Decrypted %s".formatted(delegate.getDescription());
        }
    }
}
