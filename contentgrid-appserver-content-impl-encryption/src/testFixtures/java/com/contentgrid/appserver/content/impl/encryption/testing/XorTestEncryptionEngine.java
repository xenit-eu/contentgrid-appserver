package com.contentgrid.appserver.content.impl.encryption.testing;

import com.contentgrid.appserver.content.api.ContentReader;
import com.contentgrid.appserver.content.api.ContentReference;
import com.contentgrid.appserver.content.api.UnreadableContentException;
import com.contentgrid.appserver.content.api.range.ResolvedContentRange;
import com.contentgrid.appserver.content.impl.encryption.engine.ContentEncryptionEngine;
import com.contentgrid.appserver.content.impl.encryption.engine.DataEncryptionAlgorithm;
import com.contentgrid.appserver.content.impl.encryption.keys.KeyBytes;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
    public OutputStream encrypt(OutputStream ciphertextStream, EncryptionParameters encryptionParameters) {
        var xorByte = encryptionParameters.getSecretKey().getKeyBytes()[0];
        encryptionParameters.destroy();

        return new FilterOutputStream(ciphertextStream) {
            @Override
            public void write(int b) throws IOException {
                super.write(b ^ xorByte);
            }
        };
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
                return new FilterInputStream(reader.getContentInputStream()) {

                    @Override
                    public int read() throws IOException {
                        var data = super.read();
                        if(data >= 0) {
                            return ((byte)data) ^ xorByte;
                        }
                        return data;
                    }
                };
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
}
