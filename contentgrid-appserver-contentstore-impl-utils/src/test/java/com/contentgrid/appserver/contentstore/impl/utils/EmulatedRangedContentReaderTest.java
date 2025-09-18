package com.contentgrid.appserver.contentstore.impl.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import com.contentgrid.appserver.contentstore.api.UnreadableContentException;
import com.contentgrid.appserver.contentstore.api.range.ContentRangeRequest;
import com.contentgrid.appserver.contentstore.api.range.UnsatisfiableContentRangeException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

class EmulatedRangedContentReaderTest {
    private static final byte[] FULL_DATA = "This is a test string".getBytes(StandardCharsets.UTF_8);

    @Test
    void readPartialRange() throws IOException, UnreadableContentException, UnsatisfiableContentRangeException {
        var originalReader = new BytesContentReader(FULL_DATA);

        var rangedReader = new EmulatedRangedContentReader(originalReader,
                ContentRangeRequest.createRange(5, 9).resolve(FULL_DATA.length));

        var readData = IOUtils.readFully(rangedReader.getContentInputStream(), FULL_DATA.length);

        assertArrayEquals("\0\0\0\0\0is a \0\0\0\0\0\0\0\0\0\0\0".getBytes(StandardCharsets.UTF_8), readData);

    }
}