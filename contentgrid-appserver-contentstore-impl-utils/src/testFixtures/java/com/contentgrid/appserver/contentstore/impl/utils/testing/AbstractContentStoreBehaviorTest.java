package com.contentgrid.appserver.contentstore.impl.utils.testing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.contentgrid.appserver.contentstore.api.ContentIOException;
import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.contentstore.api.UnreadableContentException;
import com.contentgrid.appserver.contentstore.api.UnwritableContentException;
import com.contentgrid.appserver.contentstore.api.range.ContentRangeRequest;
import com.contentgrid.appserver.contentstore.api.range.ResolvedContentRange;
import com.contentgrid.appserver.contentstore.api.range.UnsatisfiableContentRangeException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Base test class that all content stores should pass
 */
public abstract class AbstractContentStoreBehaviorTest {

    protected static final byte[] TEST_BYTES = "Test data".getBytes(StandardCharsets.UTF_8);

    protected abstract ContentStore getContentStore();

    @Test
    void createNewFileAndReadBack() throws IOException, ContentIOException {
        var contentStore = getContentStore();
        var contentAccessor = contentStore.writeContent(new ByteArrayInputStream(TEST_BYTES));

        var reader = contentStore.getReader(
                contentAccessor.getReference(),
                ResolvedContentRange.fullRange(contentAccessor.getContentSize())
        );

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
            contentStore.getReader(
                            contentAccessor.getReference(),
                            ResolvedContentRange.fullRange(contentAccessor.getContentSize())
                    )
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
                    ContentRangeRequest.createRange(0).resolve(contentAccessor.getContentSize() - 1)
            );
        });

        assertThrows(UnreadableContentException.class, () -> {
            contentStore.getReader(
                    contentAccessor.getReference(),
                    ContentRangeRequest.createRange(0).resolve(contentAccessor.getContentSize() + 1)
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
    void writeLargeFile() throws UnwritableContentException {
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

        // 1 GiB written
        assertEquals(targetSize, contentAccessor.getContentSize());

        // Clean up the file again
        contentStore.remove(contentAccessor.getReference());
    }
}
