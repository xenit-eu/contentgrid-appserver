package com.contentgrid.appserver.contentstore.impl.encryption.testing;

import com.contentgrid.appserver.contentstore.api.ContentReader;
import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.UnreadableContentException;
import com.contentgrid.appserver.contentstore.api.range.ResolvedContentRange;
import com.contentgrid.appserver.contentstore.impl.encryption.engine.ContentEncryptionEngine;
import com.contentgrid.appserver.contentstore.impl.encryption.engine.DataEncryptionAlgorithm;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.KeyBytes;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.Objects;

public class XorTestEncryptionEngine implements ContentEncryptionEngine {
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final DataEncryptionAlgorithm ALGORITHM = DataEncryptionAlgorithm.of("XOR-TEST");

    @Override
    public boolean supports(DataEncryptionAlgorithm algorithm) {
        return Objects.equals(algorithm, ALGORITHM);
    }

    @Override
    public EncryptionParameters createNewParameters() {
        var xorByte = new byte[1];
        secureRandom.nextBytes(xorByte);

        return new EncryptionParameters(
                ALGORITHM,
                KeyBytes.adopt(xorByte),
                new byte[0]
        );
    }

    @Override
    public InputStream encrypt(InputStream plaintextStream, EncryptionParameters encryptionParameters) {
        var xorByte = encryptionParameters.getSecretKey().getKeyBytes()[0];
        encryptionParameters.destroy();

        return new XorInputStream(plaintextStream, xorByte);
    }

    @Override
    public ContentReader decrypt(CiphertextReaderSupplier cipherTextReaderSupplier,
            EncryptionParameters encryptionParameters, ResolvedContentRange contentRange)
            throws UnreadableContentException {
        var xorByte = encryptionParameters.getSecretKey().getKeyBytes()[0];
        encryptionParameters.destroy();

        var reader = cipherTextReaderSupplier.getReader(contentRange);
        return new ContentReader() {
            @Override
            public InputStream getContentInputStream() throws UnreadableContentException {
                return new XorInputStream(reader.getContentInputStream(), xorByte);
            }

            @Override
            public ContentReference getReference() {
                return reader.getReference();
            }

            @Override
            public long getContentSize() {
                return reader.getContentSize();
            }

            @Override
            public String getDescription() {
                return "Decrypted "+reader.getDescription();
            }
        };
    }

    private static class XorInputStream extends FilterInputStream {
        private final byte xorByte;

        public XorInputStream(InputStream in, byte xorByte) {
            super(in);
            this.xorByte = xorByte;
        }

        @Override
        public int read() throws IOException {
            var data = super.read();
            if (data >= 0) {
                return ((data & 0xff) ^ xorByte) & 0xff;
            }
            return data;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            var result = super.read(b, off, len);
            for (int i = off; i < result; i++) {
                b[i] ^= xorByte;
            }
            return result;
        }
    }
}
