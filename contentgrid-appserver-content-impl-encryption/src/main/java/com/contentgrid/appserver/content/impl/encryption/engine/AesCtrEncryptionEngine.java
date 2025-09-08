package com.contentgrid.appserver.content.impl.encryption.engine;

import com.contentgrid.appserver.content.api.ContentReader;
import com.contentgrid.appserver.content.api.ContentReference;
import com.contentgrid.appserver.content.api.UnreadableContentException;
import com.contentgrid.appserver.content.api.range.ResolvedContentRange;
import com.contentgrid.appserver.content.impl.encryption.UndecryptableContentException;
import com.contentgrid.appserver.content.impl.encryption.keys.KeyBytes;
import com.contentgrid.appserver.content.impl.utils.SkippingInputStream;
import com.contentgrid.appserver.content.impl.utils.ZeroPrefixedInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.security.auth.Destroyable;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;

/**
 * Symmetric data encryption engine using AES-CTR encryption mode
 */
public class AesCtrEncryptionEngine implements ContentEncryptionEngine {
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final List<Integer> validKeySizeBits = List.of(128, 192, 256);
    private final int keySizeBytes;

    // This is the old name of the algorithm, which may still be present for stored data encryption keys
    private static final DataEncryptionAlgorithm LEGACY_ALGORITHM = DataEncryptionAlgorithm.of("AES");
    private static final DataEncryptionAlgorithm ALGORITHM = DataEncryptionAlgorithm.of("AES-CTR");
    private static final int AES_BLOCK_SIZE_BYTES = 16; // AES has a 128-bit block size
    private static final int IV_SIZE_BYTES = AES_BLOCK_SIZE_BYTES; // IV is the same size as a block

    public AesCtrEncryptionEngine(int keySizeBits) {
        if(!validKeySizeBits.contains(keySizeBits)) {
            throw new IllegalArgumentException(
                    "Key size must be %s".formatted(validKeySizeBits.stream()
                            .map(Objects::toString)
                            .collect(Collectors.joining(", ")))
            );
        }

        keySizeBytes = keySizeBits/8;
    }

    @Override
    public boolean supports(DataEncryptionAlgorithm algorithm) {
        return Objects.equals(algorithm, ALGORITHM) || Objects.equals(algorithm, LEGACY_ALGORITHM);
    }

    @Override
    public EncryptionParameters createNewParameters() {
        byte[] secretKey = new byte[keySizeBytes];
        secureRandom.nextBytes(secretKey);
        byte[] iv = new byte[IV_SIZE_BYTES];
        secureRandom.nextBytes(iv);
        return new EncryptionParameters(
                ALGORITHM,
                KeyBytes.adopt(secretKey),
                iv
        );
    }

    private Cipher initializeCipher(EncryptionParameters parameters, boolean forEncryption)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException {
        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        try {
            cipher.init(
                    forEncryption ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
                    new AESSecretKey(parameters.getSecretKey()),
                    new IvParameterSpec(parameters.getInitializationVector())
            );
        } finally {
            // After cipher init, the cipher should manage its own copy of the key
            // So the encryption parameters can be destroyed now
            parameters.destroy();
        }

        return cipher;
    }

    @Override
    @SneakyThrows
    public InputStream encrypt(InputStream plaintextStream, EncryptionParameters encryptionParameters) {
        return new CipherInputStream(plaintextStream, initializeCipher(encryptionParameters, true));
    }

    @Override
    public ContentReader decrypt(
            CiphertextReaderSupplier ciphertextReaderSupplier,
            EncryptionParameters encryptionParameters,
            ResolvedContentRange contentRange
    ) throws UnreadableContentException {
        var blockStartOffset = calculateBlockOffset(contentRange.getStartByte());

        var adjustedIv = adjustIvForOffset(encryptionParameters.getInitializationVector(), blockStartOffset);

        var adjustedParameters = new EncryptionParameters(
                encryptionParameters.getAlgorithm(),
                encryptionParameters.getSecretKey(),
                adjustedIv
        );

        var byteStartOffset = blockStartOffset * AES_BLOCK_SIZE_BYTES;

        var ciphertextContentReader = ciphertextReaderSupplier.getReader(contentRange);

        Cipher cipher = null;
        try {
            cipher = initializeCipher(adjustedParameters, false);
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidAlgorithmParameterException |
                 InvalidKeyException e) {
            throw new UndecryptableContentException(ciphertextContentReader.getReference(), e);
        }

        return new DecryptingContentReader(
                ciphertextContentReader,
                cipher,
                byteStartOffset
        );
    }

    private static long calculateBlockOffset(long offsetBytes) {
        return (offsetBytes - (offsetBytes % AES_BLOCK_SIZE_BYTES)) / AES_BLOCK_SIZE_BYTES;
    }

    private byte[] adjustIvForOffset(byte[] iv, long offsetBlocks) {
        // Optimization: no need to adjust the IV when we have no block offset
        if(offsetBlocks == 0) {
            return iv;
        }

        // AES-CTR works by having a separate IV for every block.
        // This block IV is built from the initial IV and the block counter.
        var initialIv = new BigInteger(1, iv);
        byte[] bigintBytes = initialIv.add(BigInteger.valueOf(offsetBlocks))
                .toByteArray();

        // Because we're using BigInteger for math here,
        // the resulting byte array may be longer (when overflowing the IV size, we should wrap around)
        // or shorter (when our IV starts with a bunch of 0)
        // It needs to be the proper length, and aligned properly
        if(bigintBytes.length == AES_BLOCK_SIZE_BYTES) {
            return bigintBytes;
        } else if(bigintBytes.length > AES_BLOCK_SIZE_BYTES) {
            // Byte array is longer, we need to cut a part of the front
            return Arrays.copyOfRange(bigintBytes, bigintBytes.length-IV_SIZE_BYTES, bigintBytes.length);
        } else {
            // Byte array is shorter, we need to pad the front with 0 bytes
            // Note that a bytes array is initialized to be all-zero by default
            byte[] ivBytes = new byte[IV_SIZE_BYTES];
            System.arraycopy(bigintBytes, 0, ivBytes, IV_SIZE_BYTES-bigintBytes.length, bigintBytes.length);
            return ivBytes;
        }
    }

    @RequiredArgsConstructor
    private static class AESSecretKey implements javax.crypto.SecretKey {
        @Delegate(types = Destroyable.class)
        private final KeyBytes keyBytes;

        @Override
        public String getAlgorithm() {
            return "AES";
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

    @RequiredArgsConstructor
    private static class DecryptingContentReader implements ContentReader {

        private final ContentReader delegate;
        private final Cipher cipher;
        private final long byteStartOffset;

        @Override
        public InputStream getContentInputStream() throws UnreadableContentException {
            return new ZeroPrefixedInputStream(
                    new CipherInputStream(
                            new SkippingInputStream(
                                    delegate.getContentInputStream(),
                                    byteStartOffset
                            ),
                            cipher
                    ),
                    byteStartOffset
            );
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
