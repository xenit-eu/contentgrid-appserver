package com.contentgrid.appserver.content.impl.utils.testing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.contentgrid.appserver.content.api.ContentIOException;
import com.contentgrid.appserver.content.api.ContentReference;
import com.contentgrid.appserver.content.api.ContentStore;
import com.contentgrid.appserver.content.api.UnreadableContentException;
import com.contentgrid.appserver.content.api.range.ContentRangeRequest;
import com.contentgrid.appserver.content.api.range.ResolvedContentRange;
import com.contentgrid.appserver.content.api.range.UnsatisfiableContentRangeException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
        var writer = contentStore.createNewWriter();

        try(var outputStream = writer.getContentOutputStream()) {
            outputStream.write(TEST_BYTES);
        }

        // Output stream can only be accessed once
        assertThrows(IllegalStateException.class, writer::getContentOutputStream);

        var reader = contentStore.getReader(
                writer.getReference(),
                ResolvedContentRange.fullRange(writer.getContentSize())
        );

        try(var inputStream = reader.getContentInputStream()) {
            assertArrayEquals(TEST_BYTES, inputStream.readAllBytes());
        }

        // Input stream can only be accessed once
        assertThrows(IllegalStateException.class, reader::getContentInputStream);
    }

    @Test
    void deleteFile() throws IOException, ContentIOException {
        var contentStore = getContentStore();
        var writer = contentStore.createNewWriter();

        try(var outputStream = writer.getContentOutputStream()) {
            outputStream.write(TEST_BYTES);
        }

        contentStore.remove(writer.getReference());
        // File can be removed multiple times without any problem
        contentStore.remove(writer.getReference());

        assertThrows(UnreadableContentException.class, () -> {
            contentStore.getReader(
                            writer.getReference(),
                            ResolvedContentRange.fullRange(writer.getContentSize())
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
    void readIncorrectFileSize() throws IOException, ContentIOException {
        var contentStore = getContentStore();
        var writer = contentStore.createNewWriter();

        try (var outputStream = writer.getContentOutputStream()) {
            outputStream.write(TEST_BYTES);
        }

        assertThrows(UnreadableContentException.class, () -> {
            contentStore.getReader(
                    writer.getReference(),
                    ContentRangeRequest.createRange(0).resolve(writer.getContentSize() - 1)
            );
        });

        assertThrows(UnreadableContentException.class, () -> {
            contentStore.getReader(
                    writer.getReference(),
                    ContentRangeRequest.createRange(0).resolve(writer.getContentSize() + 1)
            );
        });
    }

    @Test
    void readRange() throws IOException, UnsatisfiableContentRangeException, ContentIOException {
        var contentStore = getContentStore();
        var writer = contentStore.createNewWriter();

        try(var outputStream = writer.getContentOutputStream()) {
            outputStream.write(TEST_BYTES);
        }

        var reader = contentStore.getReader(
                writer.getReference(),
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
}
