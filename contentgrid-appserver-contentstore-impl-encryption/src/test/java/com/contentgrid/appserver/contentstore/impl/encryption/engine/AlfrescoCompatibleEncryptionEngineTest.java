package com.contentgrid.appserver.contentstore.impl.encryption.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.range.ResolvedContentRange;
import com.contentgrid.appserver.contentstore.impl.encryption.engine.ContentEncryptionEngine.EncryptionParameters;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.KeyBytes;
import com.contentgrid.appserver.contentstore.impl.encryption.testing.ResourceContentReader;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;

class AlfrescoCompatibleEncryptionEngineTest
{

    // the following static variables correlate to another and must have consistent order
    // KEYS = symmetric encryption keys generated in an alfresco-simple-content-stores ACS instance
    // RESOURCES = encrypted/decrypted resources (lorem ipsum-like)
    // ALGORITHMS = encryption algorithms used for resources
    // DECRYPTED_SIZES = unencrypted resource sizes
    // ENCRYPTED_SIZES = encrypted resource sizes
    private static final String[] KEYS = { "156c8bc259cd3e4ad1d9c38cf6361847", "566cf243d7ee9cf69df6ec004bf5f35c",
            "2405ae5a16b3909abb9ece58833c08bc", "72c51fcefd0a6da181dc4c98bbcc08e5", "89cf56ab800a394d95c10548065aae5d", "e39d23642ca81c68",
            "13320e7520e60e26133ef8372a0b7aad9e9e8a0bba5bf2ab" };

    private static final String[] RESOURCES = { "3d9ea5f1-807a-486a-810a-81c871adc52f.bin", "fe01c226-b391-4ec0-943f-ee290bb08e4e.bin",
            "32753a7a-ef36-4cb8-a4a5-c7f9f180f51e.bin", "bd78632e-80e2-42ed-8e05-e2f6b69a89b9.bin",
            "bc49e9ad-fcfc-403e-ba3e-2280d602a53e.bin", "ba50f10d-4df4-4ba0-9161-3038be5fab80.bin",
            "05efb43b-2808-4a1f-b25a-83f1c24f8bcf.bin" };

    private static final String[] ALGORITHMS = { "Alfresco-AES", "Alfresco-AES", "Alfresco-AES", "Alfresco-AES", "Alfresco-AES", "Alfresco-DES", "Alfresco-DESede" };

    private static final long[] DECRYPTED_SIZES = { 6094l, 4240l, 4117l, 4162l, 1001l, 2859l, 5853l };

    private static final long[] ENCRYPTED_SIZES = { 6096l, 4256l, 4128l, 4176l, 1008l, 2864l, 5856l };
    
    private static final long[] START_BYTES = { 128l, 3096l, 1234l, 3210l, 512l, 1536l, 4096l };

    private static final List<Arguments> fullComparison;
    private static final List<Arguments> rangedComparison;
    static {
        fullComparison = new ArrayList<>();
        rangedComparison = new ArrayList<>();
        for (int i = 0; i < KEYS.length; i++)
        {
            fullComparison.add(Arguments.of(ALGORITHMS[i], KEYS[i], RESOURCES[i], DECRYPTED_SIZES[i]));
            rangedComparison.add(Arguments.of(ALGORITHMS[i], KEYS[i], RESOURCES[i], DECRYPTED_SIZES[i], ENCRYPTED_SIZES[i], START_BYTES[i]));
        }
    }

    // engine is stateless
    private final AlfrescoCompatibleEncryptionEngine engine = new AlfrescoCompatibleEncryptionEngine();

    @Test
    void decryptionOnly() throws Exception
    {
        assertThatThrownBy(engine::createNewParameters).isInstanceOf(UnsupportedOperationException.class);

        var params = new EncryptionParameters(DataEncryptionAlgorithm.of("Alfresco-AES"), KeyBytes.adopt(HexFormat.of().parseHex(KEYS[0])),
                new byte[0]);
        try (InputStream is = AlfrescoCompatibleEncryptionEngineTest.class.getResourceAsStream("/alfresco/decrypted/" + RESOURCES[0]))
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
        var encryptedResource = "/alfresco/encrypted/" + RESOURCES[0];
        var params = new EncryptionParameters(DataEncryptionAlgorithm.of("AES"), KeyBytes.adopt(HexFormat.of().parseHex(KEYS[0])),
                new byte[0]);

        ResolvedContentRange contentRange = new ResolvedContentRange()
        {

            @Override
            public long getStartByte()
            {
                return 0;
            }

            // isn't really used - also hasSameContentAs cannot be limited
            @Override
            public long getEndByteInclusive()
            {
                return DECRYPTED_SIZES[0] - 1;
            }

            @Override
            public long getContentSize()
            {
                return DECRYPTED_SIZES[0];
            }
        };

        assertThatThrownBy(() -> engine.decrypt(r -> new ResourceContentReader(encryptedResource), params, contentRange)).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @FieldSource
    void fullComparison(String alg, String keyHex, String resource, long decryptedSize) throws Exception
    {
        var params = new EncryptionParameters(DataEncryptionAlgorithm.of(alg), KeyBytes.adopt(HexFormat.of().parseHex(keyHex)),
                new byte[0]);

        var encryptedResource = "/alfresco/encrypted/" + resource;
        var decryptedResource = "/alfresco/decrypted/" + resource;

        var reader = engine.decrypt(r -> new ResourceContentReader(encryptedResource), params,
                ResolvedContentRange.fullRange(decryptedSize));

        assertThat(reader.getContentInputStream())
                .hasSameContentAs(AlfrescoCompatibleEncryptionEngineTest.class.getResourceAsStream(decryptedResource));
    }

    @ParameterizedTest
    @FieldSource
    void rangedComparison(String alg, String keyHex, String resource, long decryptedSize, long encryptedSize, long startByte)
            throws Exception
    {
        var params = new EncryptionParameters(DataEncryptionAlgorithm.of(alg), KeyBytes.adopt(HexFormat.of().parseHex(keyHex)),
                new byte[0]);

        var encryptedResource = "/alfresco/encrypted/" + resource;
        var decryptedResource = "/alfresco/decrypted/" + resource;

        ResolvedContentRange contentRange = new ResolvedContentRange()
        {

            @Override
            public long getStartByte()
            {
                return startByte;
            }

            // isn't really used - also hasSameContentAs cannot be limited
            @Override
            public long getEndByteInclusive()
            {
                return decryptedSize - 1;
            }

            @Override
            public long getContentSize()
            {
                return decryptedSize;
            }
        };
        var reader = engine.decrypt(r -> new ResourceContentReader(encryptedResource), params, contentRange);
        assertThat(reader.getContentSize()).isEqualTo(encryptedSize);
        assertThat(reader.getReference()).isEqualTo(ContentReference.of(encryptedResource));
        assertThat(reader.getDescription()).isEqualTo("Decrypted resource file " + encryptedResource);

        try (var is1 = AlfrescoCompatibleEncryptionEngineTest.class.getResourceAsStream(decryptedResource))
        {
            is1.skipNBytes(contentRange.getStartByte());
            InputStream is2 = reader.getContentInputStream();
            assertThat(is2.skip(contentRange.getStartByte())).isEqualTo(contentRange.getStartByte());
            assertThat(is2).hasSameContentAs(is1);
        }
    }
}