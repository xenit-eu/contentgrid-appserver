package com.contentgrid.appserver.contentstore.impl.encryption.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contentgrid.appserver.contentstore.api.ContentReader;
import com.contentgrid.appserver.contentstore.api.range.ContentRangeRequest;
import com.contentgrid.appserver.contentstore.api.range.ResolvedContentRange;
import com.contentgrid.appserver.contentstore.impl.encryption.engine.ContentEncryptionEngine.EncryptionParameters;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.KeyBytes;
import com.contentgrid.appserver.contentstore.impl.encryption.testing.ResourceContentReader;

import java.io.InputStream;
import java.util.HexFormat;

import lombok.SneakyThrows;
import lombok.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;

class AlfrescoCompatibleEncryptionEngineTest
{

    private static final EncryptedResource[] ENCRYPTED_RESOURCES = {
            new EncryptedResource(
                    "156c8bc259cd3e4ad1d9c38cf6361847",
                    "3d9ea5f1-807a-486a-810a-81c871adc52f.bin",
                    "Alfresco-AES",
                    6094l,
                    6096l,
                    128l
            ),
            new EncryptedResource(
                    "566cf243d7ee9cf69df6ec004bf5f35c",
                    "fe01c226-b391-4ec0-943f-ee290bb08e4e.bin",
                    "Alfresco-AES",
                    4240l,
                    4256l,
                    3096l
            ),
            new EncryptedResource(
                    "2405ae5a16b3909abb9ece58833c08bc",
                    "32753a7a-ef36-4cb8-a4a5-c7f9f180f51e.bin",
                    "Alfresco-AES",
                    4117l,
                    4128l,
                    1234l
            ),
            new EncryptedResource(
                    "72c51fcefd0a6da181dc4c98bbcc08e5",
                    "bd78632e-80e2-42ed-8e05-e2f6b69a89b9.bin",
                    "Alfresco-AES",
                    4162l,
                    4176l,
                    3210l
            ),
            new EncryptedResource(
                    "89cf56ab800a394d95c10548065aae5d",
                    "bc49e9ad-fcfc-403e-ba3e-2280d602a53e.bin",
                    "Alfresco-AES",
                    1001l,
                    1008l,
                    512l
            ),
            new EncryptedResource(
                    "e39d23642ca81c68",
                    "ba50f10d-4df4-4ba0-9161-3038be5fab80.bin",
                    "Alfresco-DES",
                    2859l,
                    2864l,
                    1536l
            ),
            new EncryptedResource(
                    "13320e7520e60e26133ef8372a0b7aad9e9e8a0bba5bf2ab",
                    "05efb43b-2808-4a1f-b25a-83f1c24f8bcf.bin",
                    "Alfresco-DESede",
                    5853l,
                    5856l,
                    4096l
            )
    };

    @Value
    public static class EncryptedResource {
        String key, resource, algorithm;
        long decryptedSize, encryptedSize, startByte;

        public DataEncryptionAlgorithm getEncryptionAlgorithm() {
            return DataEncryptionAlgorithm.of(algorithm);
        }

        public KeyBytes getKeyBytes() {
            return KeyBytes.adopt(HexFormat.of().parseHex(key));
        }

        public EncryptionParameters getEncryptionParameters() {
            return new EncryptionParameters(getEncryptionAlgorithm(), getKeyBytes(), new byte[0]);
        }

        public ContentReader getEncryptedReader() {
            return new ResourceContentReader(AlfrescoCompatibleEncryptionEngine.class.getResource("alfresco/encrypted/"+resource));
        }

        public ContentReader getDecryptedReader() {
            return new ResourceContentReader(AlfrescoCompatibleEncryptionEngine.class.getResource("alfresco/decrypted/"+resource));
        }

        @SneakyThrows
        public InputStream getDecryptedResourceAsStream() {
            return getDecryptedReader().getContentInputStream();
        }

        public ContentReader getEncryptedReader(ResolvedContentRange resolvedContentRange) {
            var encryptedReader = getEncryptedReader();
            // The resolved content range must match the size of the encrypted reader
            assertThat(resolvedContentRange.getContentSize())
                    .isEqualTo(encryptedSize)
                    .isEqualTo(encryptedReader.getContentSize());
            return encryptedReader;
        }

    }

    // engine is stateless
    private final AlfrescoCompatibleEncryptionEngine engine = new AlfrescoCompatibleEncryptionEngine();

    @Test
    void decryptionOnly() throws Exception
    {
        assertThatThrownBy(engine::createNewParameters).isInstanceOf(UnsupportedOperationException.class);

        var params = ENCRYPTED_RESOURCES[0].getEncryptionParameters();
        try (InputStream is = ENCRYPTED_RESOURCES[0].getDecryptedReader().getContentInputStream())
        {
            assertThatThrownBy(() -> engine.encrypt(is, params)).isInstanceOf(UnsupportedOperationException.class);
        }
    }
    
    @Test
    void supportCheck()
    {
        // test various general algorithms and explicit modes
        // supported + unsupported
        assertThat(engine.supports(DataEncryptionAlgorithm.of("AES"))).isFalse();
        assertThat(engine.supports(DataEncryptionAlgorithm.of("Alfresco-AES"))).isTrue();
        assertThat(engine.supports(DataEncryptionAlgorithm.of("Alfresco-AES/CTR/PKCS5Padding"))).isFalse();
        assertThat(engine.supports(DataEncryptionAlgorithm.of("Alfresco-AES/CBC/PKCS5Padding"))).isTrue();
        assertThat(engine.supports(DataEncryptionAlgorithm.of("Alfresco-AES/CTR/NoPadding"))).isTrue();
        assertThat(engine.supports(DataEncryptionAlgorithm.of("Alfresco-DES"))).isTrue();
        assertThat(engine.supports(DataEncryptionAlgorithm.of("Alfresco-DESede"))).isTrue();
        assertThat(engine.supports(DataEncryptionAlgorithm.of("Alfresco-RSA"))).isFalse();

        // attempt to decrypt using unsupported algorithm (lacking prefix)
        var encryptedResource = ENCRYPTED_RESOURCES[0];
        var params = new EncryptionParameters(DataEncryptionAlgorithm.of("AES"), encryptedResource.getKeyBytes(),
                new byte[0]);

        ResolvedContentRange contentRange = ResolvedContentRange.fullRange(encryptedResource.getDecryptedSize());

        assertThatThrownBy(() -> engine.decrypt(encryptedResource::getEncryptedReader, params, contentRange)).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @FieldSource("ENCRYPTED_RESOURCES")
    void fullComparison(EncryptedResource encryptedResource) throws Exception
    {
        var reader = engine.decrypt(
                encryptedResource::getEncryptedReader,
                encryptedResource.getEncryptionParameters(),
                ResolvedContentRange.fullRange(encryptedResource.getDecryptedSize())
        );

        assertThat(reader.getContentInputStream())
                .hasSameContentAs(encryptedResource.getDecryptedResourceAsStream());
    }

    @ParameterizedTest
    @FieldSource("ENCRYPTED_RESOURCES")
    void rangedComparison(EncryptedResource encryptedResource)
            throws Exception
    {
        var reader = engine.decrypt(
                encryptedResource::getEncryptedReader,
                encryptedResource.getEncryptionParameters(),
                ContentRangeRequest.createRange(encryptedResource.getStartByte())
                        .resolve(encryptedResource.getDecryptedSize())
        );
        // The size of the reader is the *decrypted* size (because that is stored in the database and shown to the user)
        assertThat(reader.getContentSize()).isEqualTo(encryptedResource.getDecryptedSize());
        // But the content reference is of course the same as the encrypted content (because that's what is stored in the database)
        assertThat(reader.getReference()).isEqualTo(encryptedResource.getEncryptedReader().getReference());
        assertThat(reader.getDescription()).isEqualTo("Decrypted " + encryptedResource.getEncryptedReader().getDescription());

        try (
                var decryptedStream = reader.getContentInputStream();
                var verificationStream = encryptedResource.getDecryptedResourceAsStream()
        )
        {
            verificationStream.skipNBytes(encryptedResource.getStartByte());
            assertThat(decryptedStream.skip(encryptedResource.getStartByte()))
                    .isEqualTo(encryptedResource.getStartByte());
            assertThat(decryptedStream).hasSameContentAs(verificationStream);
        }
    }
}