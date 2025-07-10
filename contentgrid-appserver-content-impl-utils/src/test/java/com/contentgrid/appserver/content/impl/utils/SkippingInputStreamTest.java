package com.contentgrid.appserver.content.impl.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SkippingInputStreamTest {
    private static final byte[] FULL_DATA = "This is a test string".getBytes(StandardCharsets.UTF_8);
    InputStream inputStream;

    @BeforeEach
    void setupInputStream() {
        inputStream = new SkippingInputStream(new ByteArrayInputStream(FULL_DATA), 5);
    }

    @Test
    void skipsAdditionalBytesOnRead() throws IOException {
        assertEquals(FULL_DATA[5] ,inputStream.read());
    }

    @Test
    void skipsAdditionalBytesOnFullRead() throws IOException {
        byte[] readData = new byte[FULL_DATA.length - 5];
        IOUtils.readFully(inputStream, readData);

        assertEquals(FULL_DATA[5], readData[0]);
    }

    @Test
    void onlySkipsOnce() throws IOException {
        assertEquals(3, inputStream.skip(3));

        // 5 bytes skipped from base skip; 3 additional ones from the skip above
        assertEquals(FULL_DATA[5+3], inputStream.read());
    }

}