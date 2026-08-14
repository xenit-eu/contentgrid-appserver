package com.contentgrid.appserver.contentstore.impl.utils.testing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import com.contentgrid.appserver.contentstore.api.ContentIOException;
import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.contentstore.api.UnreadableContentException;
import com.contentgrid.appserver.contentstore.api.UnwritableContentException;
import com.contentgrid.appserver.contentstore.api.range.ContentRangeRequest;
import com.contentgrid.appserver.contentstore.api.range.UnsatisfiableContentRangeException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Base test class that all content stores should pass
 */
public abstract class AbstractContentStoreBehaviorTest {

    protected static final byte[] TEST_BYTES = "Test data".getBytes(StandardCharsets.UTF_8);

    /**
     * Bigger than the chunk size a store may internally split content into, so reading this back also
     * covers stores that have to reassemble an object out of several parts. Lowering this below the part
     * size a store uploads with (50 MiB for S3) silently stops covering that.
     */
    protected static final long LARGE_SIZE = 60L * 1024 * 1024;

    protected abstract ContentStore getContentStore();

    @Test
    void createNewFileAndReadBack() throws IOException, ContentIOException {
        var contentStore = getContentStore();
        var contentAccessor = contentStore.writeContent(new ByteArrayInputStream(TEST_BYTES));

        var reader = contentStore.getReader(contentAccessor.getReference());

        try(var inputStream = reader.getContentInputStream()) {
            assertArrayEquals(TEST_BYTES, inputStream.readAllBytes());
        }

        // Input stream can only be accessed once
        assertThrows(IllegalStateException.class, reader::getContentInputStream);
    }

    @Test
    void deleteFile() throws ContentIOException {
        var contentStore = getContentStore();
        var contentAccessor = contentStore.writeContent(new ByteArrayInputStream(TEST_BYTES));

        contentStore.remove(contentAccessor.getReference());
        // File can be removed multiple times without any problem
        contentStore.remove(contentAccessor.getReference());

        assertThrows(UnreadableContentException.class, () -> {
            contentStore.getReader(contentAccessor.getReference())
                    .getContentInputStream();
        });
    }

    @Test
    void readNonExistent() {
        var contentStore = getContentStore();
        assertThrows(UnreadableContentException.class, () -> {
            contentStore.getReader(ContentReference.of("non-existing"), ContentRangeRequest.createRange(0).resolve(5))
                    .getContentInputStream();
        });
    }

    @Test
    void readIncorrectFileSize() throws ContentIOException {
        var contentStore = getContentStore();
        var contentAccessor = contentStore.writeContent(new ByteArrayInputStream(TEST_BYTES));

        assertThrows(UnreadableContentException.class, () -> {
            contentStore.getReader(
                    contentAccessor.getReference(),
                    ContentRangeRequest.createRange(0).resolve(TEST_BYTES.length - 1)
            );
        });

        assertThrows(UnreadableContentException.class, () -> {
            contentStore.getReader(
                    contentAccessor.getReference(),
                    ContentRangeRequest.createRange(0).resolve(TEST_BYTES.length + 1)
            );
        });
    }

    @Test
    void readRange() throws IOException, UnsatisfiableContentRangeException, ContentIOException {
        var contentStore = getContentStore();
        var contentAccessor = contentStore.writeContent(new ByteArrayInputStream(TEST_BYTES));

        var reader = contentStore.getReader(
                contentAccessor.getReference(),
                ContentRangeRequest.createRange(5, 7).resolve(TEST_BYTES.length)
        );
        try(var inputStream = reader.getContentInputStream()) {
            inputStream.skipNBytes(5);
            assertArrayEquals(new byte[] {
                    TEST_BYTES[5],
                    TEST_BYTES[6],
                    TEST_BYTES[7],
            }, inputStream.readNBytes(3));
        }
    }

    @Test
    void writeLargeFile() throws UnwritableContentException, UnreadableContentException {
        var contentStore = getContentStore();

        var targetSize = 1000L * 1024 * 1024; // 1 GiB
        var largeDataStream = new java.io.InputStream() {
            private long bytesRead = 0;

            @Override
            public int read() {
                if (bytesRead >= targetSize) {
                    return -1;
                }
                bytesRead++;
                return 0xbb;
            }

            @Override
            public int read(byte[] b, int off, int len) {
                if (bytesRead >= targetSize) {
                    return -1;
                }

                int toRead = (int) Math.min(len, targetSize - bytesRead);
                Arrays.fill(b, off, off + toRead, (byte) 0xbb);
                bytesRead += toRead;
                return toRead;
            }
        };

        var contentAccessor = contentStore.writeContent(largeDataStream);

        // Clean up the file again
        contentStore.remove(contentAccessor.getReference());
    }

    @Test
    void writeLargeFileAndReadBack() throws ContentIOException, IOException {
        var contentStore = getContentStore();

        var contentAccessor = contentStore.writeContent(patternStream(LARGE_SIZE));
        try (var inputStream = contentStore.getReader(contentAccessor.getReference()).getContentInputStream()) {
            assertPatternStream(inputStream, LARGE_SIZE);
        } finally {
            contentStore.remove(contentAccessor.getReference());
        }
    }

    /**
     * Reads a range far from the start of the content, at an offset that is deliberately not a multiple of
     * any likely block or chunk size. Positions are absolute, so a store that loses precision on large
     * offsets, or that aligns to a boundary without compensating, returns the wrong bytes here.
     */
    @Test
    void readRangeAtLargeOffset() throws IOException, UnsatisfiableContentRangeException, ContentIOException {
        var contentStore = getContentStore();
        var startByte = LARGE_SIZE - 8_388_593; // 8 MiB - 15 bytes before the end
        var endByteInclusive = startByte + 19;

        var contentAccessor = contentStore.writeContent(patternStream(LARGE_SIZE));
        try {
            var range = ContentRangeRequest.createRange(startByte, endByteInclusive).resolve(LARGE_SIZE);
            try (var inputStream = contentStore.getReader(contentAccessor.getReference(), range)
                    .getContentInputStream()) {
                inputStream.skipNBytes(startByte);
                var bytes = inputStream.readNBytes(20);
                assertEquals(20, bytes.length);
                for (int i = 0; i < bytes.length; i++) {
                    assertEquals(patternByte(startByte + i), bytes[i], "byte at " + (startByte + i));
                }
            }
        } finally {
            contentStore.remove(contentAccessor.getReference());
        }
    }

    /**
     * The byte that a {@link #patternStream} holds at the given position. Position-dependent, so a stream
     * that is reassembled out of order or padded with zeroes does not go unnoticed.
     */
    protected static byte patternByte(long position) {
        return (byte) (position % 251);
    }

    protected static InputStream patternStream(long size) {
        return new InputStream() {
            private long position = 0;

            @Override
            public int read() {
                return position >= size ? -1 : Byte.toUnsignedInt(patternByte(position++));
            }

            @Override
            public int read(byte[] b, int off, int len) {
                if (position >= size) {
                    return -1;
                }
                int toRead = (int) Math.min(len, size - position);
                for (int i = 0; i < toRead; i++) {
                    b[off + i] = patternByte(position + i);
                }
                position += toRead;
                return toRead;
            }
        };
    }

    protected static void assertPatternStream(InputStream inputStream, long expectedSize) throws IOException {
        var buffer = new byte[64 * 1024];
        long position = 0;
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            for (int i = 0; i < read; i++) {
                if (buffer[i] != patternByte(position + i)) {
                    fail("Byte mismatch at position " + (position + i));
                }
            }
            position += read;
        }
        assertEquals(expectedSize, position);
    }
}
