package com.contentgrid.appserver.contentstore.impl.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ZeroPrefixedInputStreamTest {
    private static final byte[] FULL_DATA = "This is a test string".getBytes(StandardCharsets.UTF_8);
    InputStream inputStream;

    @BeforeEach
    void setupInputStream() {
        inputStream = new ZeroPrefixedInputStream(new ByteArrayInputStream(FULL_DATA), 5);
    }

    @Test
    void readsZeroPrefix() throws IOException {
        assertEquals(0 ,inputStream.read());
        assertEquals(0 ,inputStream.read());
        assertEquals(0 ,inputStream.read());
        assertEquals(0 ,inputStream.read());
        assertEquals(0 ,inputStream.read());

        assertEquals(FULL_DATA[0] ,inputStream.read());
    }

    @Test
    void readsZeroBytesOnFullRead() throws IOException {
        byte[] readData = new byte[FULL_DATA.length + 5];
        IOUtils.readFully(inputStream, readData);

        assertEquals(0, readData[0]);
        assertEquals(FULL_DATA[0], readData[5]);
    }

    @Test
    void skipsZeroBytesPartially() throws IOException {
        assertEquals(3, inputStream.skip(3));

        // 3 bytes skipped of the 5 prefixed ones: 2 additional zero-bytes, followed by data
        assertEquals(0, inputStream.read());
        assertEquals(0, inputStream.read());
        assertEquals(FULL_DATA[0], inputStream.read());
    }

    @Test
    void skipsZeroBytesFully() throws IOException {
        assertEquals(5, inputStream.skip(5));

        // all bytes skipped of the prefixed ones
        assertEquals(FULL_DATA[0], inputStream.read());
    }

    @Test
    void skipsZeroBytesAndMore() throws IOException {
        assertEquals(10, inputStream.skip(10));

        // 5 prefixed bytes skipped + 5 data bytes skipped
        assertEquals(FULL_DATA[5], inputStream.read());
    }
}