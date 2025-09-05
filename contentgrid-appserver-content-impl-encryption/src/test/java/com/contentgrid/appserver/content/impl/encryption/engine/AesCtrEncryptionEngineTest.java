package com.contentgrid.appserver.content.impl.encryption.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.content.api.ContentReader;
import com.contentgrid.appserver.content.api.ContentReference;
import com.contentgrid.appserver.content.api.UnreadableContentException;
import com.contentgrid.appserver.content.api.range.ContentRangeRequest;
import com.contentgrid.appserver.content.api.range.ResolvedContentRange;
import com.contentgrid.appserver.content.api.range.UnsatisfiableContentRangeException;
import com.contentgrid.appserver.content.impl.encryption.engine.ContentEncryptionEngine.EncryptionParameters;
import com.contentgrid.appserver.content.impl.encryption.keys.KeyBytes;
import com.contentgrid.appserver.content.impl.encryption.testing.AbstractEncryptionEngineTest;
import com.contentgrid.appserver.content.impl.utils.EmulatedRangedContentReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.stream.Stream;
import lombok.Getter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class AesCtrEncryptionEngineTest extends AbstractEncryptionEngineTest {
    // See test vectors of NIST SP 800-38A (https://doi.org/10.6028/NIST.SP.800-38A)
    // CTR example vectors
    private static final byte[] KEY = HexFormat.of().parseHex("2b7e151628aed2a6abf7158809cf4f3c");
    private static final byte[] IV = HexFormat.of().parseHex("f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff");

    private static final byte[] BLOCK_1_PLAIN = HexFormat.of().parseHex("6bc1bee22e409f96e93d7e117393172a");
    private static final byte[] BLOCK_1_CIPHER = HexFormat.of().parseHex("874d6191b620e3261bef6864990db6ce");
    private static final byte[] BLOCK_2_PLAIN = HexFormat.of().parseHex("ae2d8a571e03ac9c9eb76fac45af8e51");
    private static final byte[] BLOCK_2_CIPHER = HexFormat.of().parseHex("9806f66b7970fdff8617187bb9fffdff");
    private static final byte[] BLOCK_3_PLAIN = HexFormat.of().parseHex("30c81c46a35ce411e5fbc1191a0a52ef");
    private static final byte[] BLOCK_3_CIPHER = HexFormat.of().parseHex("5ae4df3edbd5d35e5b4f09020db03eab");
    private static final byte[] BLOCK_4_PLAIN = HexFormat.of().parseHex("f69f2445df4f9b17ad2b417be66c3710");
    private static final byte[] BLOCK_4_CIPHER = HexFormat.of().parseHex("1e031dda2fbe03d1792170a0f3009cee");

    private static final byte[] PLAINTEXT = concat(BLOCK_1_PLAIN, BLOCK_2_PLAIN, BLOCK_3_PLAIN, BLOCK_4_PLAIN);
    private static final byte[] CIPHERTEXT = concat(BLOCK_1_CIPHER, BLOCK_2_CIPHER, BLOCK_3_CIPHER, BLOCK_4_CIPHER);

    @ParameterizedTest
    @CsvSource({
            "128,16",
            "192,24",
            "256,32"
    })
    void encryptionParameters(int keySizeBits, int keyLength) {
        var engine = new AesCtrEncryptionEngine(keySizeBits);

        var parameters = engine.createNewParameters();

        assertThat(parameters.getSecretKey().getKeyBytes()).hasSize(keyLength);
        assertThat(parameters.getInitializationVector()).hasSize(16);
    }

    @Test
    void encryptTestVector() throws IOException {
        var engine = new AesCtrEncryptionEngine(128);

        var encryptedStream = new ByteArrayOutputStream();

        var keyBytes = KeyBytes.copy(KEY);

        var plainStream = engine.encrypt(encryptedStream, new EncryptionParameters(
                DataEncryptionAlgorithm.of("AES-CTR"),
                keyBytes,
                IV
        ));

        // Key is destroyed after initialization
        assertThat(keyBytes.isDestroyed()).isTrue();

        plainStream.write(PLAINTEXT);

        assertThat(encryptedStream.toByteArray()).isEqualTo(CIPHERTEXT);
    }

    @Test
    void decryptTestVector() throws UnreadableContentException {
        var engine = new AesCtrEncryptionEngine(128);

        var keyBytes = KeyBytes.copy(KEY);

        var plainStream = engine.decrypt(req -> new MockContentReader(CIPHERTEXT), new EncryptionParameters(
                DataEncryptionAlgorithm.of("AES-CTR"),
                keyBytes,
                IV
        ), ResolvedContentRange.fullRange(PLAINTEXT.length));

        // Key is destroyed after initialization
        assertThat(keyBytes.isDestroyed()).isTrue();

        assertThat(plainStream.getContentInputStream()).hasBinaryContent(PLAINTEXT);
    }

    public static Stream<Arguments> decryptPartialWeirdIV() {
        return Stream.of(
                Arguments.argumentSet("normal IV", HexFormat.of().parseHex("4ffffffffffffffffffffffffffffffe")),
                Arguments.argumentSet("starting with zeroes", HexFormat.of().parseHex("000000f3f4f5f6f7f8f9fafbfcfdfeff")),
                Arguments.argumentSet("wraps around", HexFormat.of().parseHex("fffffffffffffffffffffffffffffffe"))
        );
    }

    @ParameterizedTest
    @MethodSource
    void decryptPartialWeirdIV(byte[] iv)
            throws IOException, UnsatisfiableContentRangeException, UnreadableContentException {
        var engine = new AesCtrEncryptionEngine(128);

        var encryptionParams = new EncryptionParameters(
                DataEncryptionAlgorithm.of("AES-CTR"),
                KeyBytes.copy(KEY),
                iv
        );
        // Needs to be a separate copy, because KeyBytes gets destroyed after usage
        var decryptionParams = new EncryptionParameters(
                DataEncryptionAlgorithm.of("AES-CTR"),
                KeyBytes.copy(KEY),
                iv
        );

        var encryptedStream = new ByteArrayOutputStream();
        var plainStream = engine.encrypt(encryptedStream, encryptionParams);
        plainStream.write(PLAINTEXT);
        var encrypted = encryptedStream.toByteArray();

        var offsetStart = BLOCK_1_PLAIN.length + BLOCK_2_PLAIN.length;

        var decrypted = engine.decrypt(req -> onlyByteRange(encrypted, req), decryptionParams, ContentRangeRequest.createRange(offsetStart).resolve(PLAINTEXT.length));

        try(var decryptedStream = decrypted.getContentInputStream()) {
            // We have no use for the first bytes when we have not requested them
            decryptedStream.skipNBytes(offsetStart);

            var decryptedBytes = decryptedStream.readAllBytes();

            assertThat(decryptedBytes).isEqualTo(concat(BLOCK_3_PLAIN, BLOCK_4_PLAIN));
        }

    }

    public static Stream<Arguments> decryptPartial() {
        return Stream.of(
                Arguments.argumentSet("starting from full block", ContentRangeRequest.createRange(BLOCK_1_PLAIN.length+BLOCK_2_PLAIN.length)),
                Arguments.argumentSet("starting from middle of a block", ContentRangeRequest.createRange(BLOCK_1_PLAIN.length+BLOCK_2_PLAIN.length/2)),
                Arguments.argumentSet("ending on full block", ContentRangeRequest.createRange(0, BLOCK_1_PLAIN.length+BLOCK_2_PLAIN.length)),
                Arguments.argumentSet("ending on middle of a block", ContentRangeRequest.createRange(0, BLOCK_1_PLAIN.length+BLOCK_2_PLAIN.length+BLOCK_3_PLAIN.length/2))
        );
    }

    @ParameterizedTest
    @MethodSource
    void decryptPartial(ContentRangeRequest rangeRequest)
            throws UnsatisfiableContentRangeException, UnreadableContentException, IOException {
        var engine = new AesCtrEncryptionEngine(128);

        var decryptionParams = new EncryptionParameters(
                DataEncryptionAlgorithm.of("AES-CTR"),
                KeyBytes.copy(KEY),
                IV
        );
        var resolvedRange = rangeRequest.resolve(PLAINTEXT.length);

        var decrypted = engine.decrypt(req -> onlyByteRange(CIPHERTEXT, req), decryptionParams, resolvedRange);

        try(
                var decryptedStream = decrypted.getContentInputStream();
                var originalStream = onlyByteRange(PLAINTEXT, resolvedRange).getContentInputStream()
        ) {
            // We have no use for the first bytes when we have not requested them
            decryptedStream.skipNBytes(resolvedRange.getStartByte());
            originalStream.skipNBytes(resolvedRange.getStartByte());

            var decryptedBytes = decryptedStream.readNBytes((int) resolvedRange.getRangeSize());
            var originalBytes = originalStream.readNBytes((int)resolvedRange.getRangeSize());

            assertThat(decryptedBytes).isEqualTo(originalBytes);

        }

    }

    private static byte[] concat(byte[]... blocks) {
        var totalLength = Arrays.stream(blocks).mapToInt(b -> b.length).sum();
        var fullBuffer = ByteBuffer.allocate(totalLength);

        for (var block : blocks) {
            fullBuffer.put(block);
        }

        return fullBuffer.array();
    }

    private static ContentReader onlyByteRange(byte[] encrypted, ResolvedContentRange req) {
        return new EmulatedRangedContentReader(
                new MockContentReader(encrypted),
                req
        );
    }

    @Override
    protected ContentEncryptionEngine getContentEncryptionEngine() {
        return new AesCtrEncryptionEngine(128);
    }

    @Getter
    private static class MockContentReader implements ContentReader {
        private final InputStream contentInputStream;
        private final long contentSize;

        public MockContentReader(byte[] ciphertext) {
            contentInputStream = new ByteArrayInputStream(ciphertext);
            contentSize = ciphertext.length;
        }

        @Override
        public ContentReference getReference() {
            return null;
        }

        @Override
        public String getDescription() {
            return "";
        }
    }
}