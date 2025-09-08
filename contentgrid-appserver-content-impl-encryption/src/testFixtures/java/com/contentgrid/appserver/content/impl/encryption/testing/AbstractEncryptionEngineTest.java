package com.contentgrid.appserver.content.impl.encryption.testing;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.content.api.UnreadableContentException;
import com.contentgrid.appserver.content.api.range.ContentRangeRequest;
import com.contentgrid.appserver.content.api.range.ResolvedContentRange;
import com.contentgrid.appserver.content.api.range.UnsatisfiableContentRangeException;
import com.contentgrid.appserver.content.impl.encryption.engine.ContentEncryptionEngine;
import com.contentgrid.appserver.content.impl.encryption.engine.ContentEncryptionEngine.EncryptionParameters;
import com.contentgrid.appserver.content.impl.utils.EmulatedRangedContentReader;
import com.contentgrid.appserver.content.impl.utils.testing.MockContentReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public abstract class AbstractEncryptionEngineTest {

    private static final byte[] TEST_DATA;

    static {
        TEST_DATA = new byte[256];
        for(int i = 0; i < 256; i++) {
            TEST_DATA[i] = (byte) (i&0xff);
        }
    }

    protected abstract ContentEncryptionEngine getContentEncryptionEngine();

    @Test
    void generatesSupportedParameters() {
        var params = getContentEncryptionEngine().createNewParameters();
        assertThat(params).isNotNull();

        assertThat(getContentEncryptionEngine().supports(params.getAlgorithm())).isTrue();
    }

    private ByteArrayOutputStream encrypt(EncryptionParameters params) throws IOException {
        // Copy, because secret key gets destroyed during encryption
        var keyCopy = params.getSecretKey().clone();
        var encryptParams = new EncryptionParameters(
                params.getAlgorithm(),
                keyCopy,
                params.getInitializationVector()
        );

        var plainInputStream = new ByteArrayInputStream(TEST_DATA);
        var encryptedInputStream = getContentEncryptionEngine().encrypt(plainInputStream, encryptParams);

        assertThat(encryptParams.isDestroyed()).isTrue();

        var encryptedStream = new ByteArrayOutputStream();
        try (encryptedInputStream) {
            encryptedInputStream.transferTo(encryptedStream);
        }

        // Some data should have been written
        assertThat(encryptedStream.size()).isGreaterThan(0);
        return encryptedStream;
    }

    @Test
    void encryptAndDecrypt() throws IOException, UnreadableContentException {
        var params = getContentEncryptionEngine().createNewParameters();

        var encryptedStream = encrypt(params);
        // And the data should not be the plain text
        assertThat(encryptedStream.toString(StandardCharsets.UTF_8)).doesNotContain(new String(TEST_DATA, StandardCharsets.UTF_8));

        var mockReader = new MockContentReader(encryptedStream.toByteArray());

        var decrypted = getContentEncryptionEngine().decrypt(
                req -> mockReader,
                params,
                ResolvedContentRange.fullRange(TEST_DATA.length)
        );

        assertThat(params.isDestroyed()).isTrue();

        assertThat(decrypted.getContentSize()).isEqualTo(TEST_DATA.length);
        assertThat(decrypted.getReference()).isEqualTo(mockReader.getReference());
        assertThat(decrypted.getContentInputStream()).hasBinaryContent(TEST_DATA);
    }

    public static Stream<Arguments> decryptPartialRange() {
        return Stream.of(
                Arguments.argumentSet("begin", ContentRangeRequest.createRange(0, TEST_DATA.length/8)),
                Arguments.argumentSet("middle", ContentRangeRequest.createRange(TEST_DATA.length/8, TEST_DATA.length/4)),
                Arguments.argumentSet("end", ContentRangeRequest.createSuffixRange(TEST_DATA.length/8))
        );
    }

    @ParameterizedTest
    @MethodSource
    void decryptPartialRange(ContentRangeRequest range)
            throws IOException, UnsatisfiableContentRangeException, UnreadableContentException {
        var params = getContentEncryptionEngine().createNewParameters();

        var encryptedStream = encrypt(params);

        var mockReader = new MockContentReader(encryptedStream.toByteArray());

        var resolvedRange = range.resolve(TEST_DATA.length);

        var decrypted = getContentEncryptionEngine().decrypt(
                req -> new EmulatedRangedContentReader(mockReader, resolvedRange),
                params,
                resolvedRange
        );

        assertThat(decrypted.getContentSize()).isEqualTo(TEST_DATA.length);
        assertThat(decrypted.getReference()).isEqualTo(mockReader.getReference());

        try(var decryptedContent = decrypted.getContentInputStream()) {
            decryptedContent.skipNBytes(resolvedRange.getStartByte());

            var subset = new byte[Math.toIntExact(resolvedRange.getRangeSize())];
            System.arraycopy(TEST_DATA, (int)resolvedRange.getStartByte(), subset, 0, (int)resolvedRange.getRangeSize());

            assertThat(decryptedContent.readNBytes(Math.toIntExact(resolvedRange.getRangeSize()))).isEqualTo(subset);
        }

    }

}
