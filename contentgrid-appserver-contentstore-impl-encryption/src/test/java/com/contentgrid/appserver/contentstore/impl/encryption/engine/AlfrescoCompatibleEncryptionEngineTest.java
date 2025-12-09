package com.contentgrid.appserver.contentstore.impl.encryption.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.contentstore.api.ContentReader;
import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.range.ResolvedContentRange;
import com.contentgrid.appserver.contentstore.impl.encryption.engine.ContentEncryptionEngine.EncryptionParameters;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.KeyBytes;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.ValueSource;

class AlfrescoCompatibleEncryptionEngineTest
{

    // KEYS, RESOURCES, SIZES all correlate to another and must have consistent order
    // KEYS = symmetric encryption keys generated in an alfresco-simple-contentstores ACS instance
    // RESOURCES = encrypted/decrypted resources (lorem ipsum-like)
    // SIZES = unencrypted resource sizes
    private static final String[] KEYS = { "156c8bc259cd3e4ad1d9c38cf6361847", "566cf243d7ee9cf69df6ec004bf5f35c",
            "2405ae5a16b3909abb9ece58833c08bc", "72c51fcefd0a6da181dc4c98bbcc08e5", "89cf56ab800a394d95c10548065aae5d" };

    private static final String[] RESOURCES = { "3d9ea5f1-807a-486a-810a-81c871adc52f.bin", "fe01c226-b391-4ec0-943f-ee290bb08e4e.bin",
            "32753a7a-ef36-4cb8-a4a5-c7f9f180f51e.bin", "bd78632e-80e2-42ed-8e05-e2f6b69a89b9.bin",
            "bc49e9ad-fcfc-403e-ba3e-2280d602a53e.bin" };

    private static final long[] SIZES = { 6094l, 4240l, 4117l, 4162l, 1001l };

    private static final List<Arguments> RANGED_ARGUMENTS = Arrays.asList(Arguments.of(0, 128l), Arguments.of(1, 3096l),
            Arguments.of(2, 1234l), Arguments.of(3, 3210l), Arguments.of(4, 512l));

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 2, 3, 4 })
    void fullComparison(int resIdx) throws Exception
    {
        var engine = new AlfrescoCompatibleEncryptionEngine();
        var params = new EncryptionParameters(DataEncryptionAlgorithm.of("AES"), KeyBytes.adopt(HexFormat.of().parseHex(KEYS[resIdx])),
                new byte[0]);

        var encryptedResource = "/alfresco/encrypted/" + RESOURCES[resIdx];
        var decryptedResource = "/alfresco/decrypted/" + RESOURCES[resIdx];

        var reader = engine.decrypt(r -> new ResourceContentReader(encryptedResource), params,
                ResolvedContentRange.fullRange(SIZES[resIdx]));

        assertThat(reader.getContentInputStream())
                .hasSameContentAs(AlfrescoCompatibleEncryptionEngineTest.class.getResourceAsStream(decryptedResource));
    }

    @ParameterizedTest
    @FieldSource("RANGED_ARGUMENTS")
    void rangedComparison(int resIdx, long startByte) throws Exception
    {
        var engine = new AlfrescoCompatibleEncryptionEngine();
        var params = new EncryptionParameters(DataEncryptionAlgorithm.of("AES"), KeyBytes.adopt(HexFormat.of().parseHex(KEYS[resIdx])),
                new byte[0]);

        var encryptedResource = "/alfresco/encrypted/" + RESOURCES[resIdx];
        var decryptedResource = "/alfresco/decrypted/" + RESOURCES[resIdx];

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
                return SIZES[resIdx] - 1;
            }

            @Override
            public long getContentSize()
            {
                return SIZES[resIdx];
            }
        };
        var reader = engine.decrypt(r -> new ResourceContentReader(encryptedResource), params, contentRange);

        try (var is = AlfrescoCompatibleEncryptionEngineTest.class.getResourceAsStream(decryptedResource))
        {
            is.skipNBytes(contentRange.getStartByte());
            assertThat(reader.getContentInputStream()).hasSameContentAs(is);
        }
    }

    private static class ResourceContentReader implements ContentReader
    {

        private final String resourceName;

        private final long contentSize;

        public ResourceContentReader(String resourceName)
        {
            this.resourceName = resourceName;
            long size = 0;
            try (InputStream is = getContentInputStream())
            {
                byte[] buf = new byte[1024];
                int bytesRead = 0;
                while ((bytesRead = is.read(buf)) != -1)
                {
                    size += bytesRead;
                }
            }
            catch (IOException ioex)
            {
                throw new RuntimeException(ioex);
            }
            this.contentSize = size;
        }

        @Override
        public ContentReference getReference()
        {
            return null;
        }

        @Override
        public String getDescription()
        {
            return "";
        }

        @Override
        public long getContentSize()
        {
            return this.contentSize;
        }

        @Override
        public InputStream getContentInputStream()
        {
            return AlfrescoCompatibleEncryptionEngineTest.class.getResourceAsStream(resourceName);
        }
    }
}