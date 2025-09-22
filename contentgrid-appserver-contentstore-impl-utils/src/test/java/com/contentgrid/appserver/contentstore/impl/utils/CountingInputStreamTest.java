package com.contentgrid.appserver.contentstore.impl.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class CountingInputStreamTest {
    @Test
    void bytesRead() throws IOException {
        var testData = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 };
        var bais = new ByteArrayInputStream(testData);
        var is = new CountingInputStream(bais);

        // Read single byte
        is.read();
        assertEquals(1, is.getSize());

        // Read byte array
        byte[] buffer = new byte[4];
        is.read(buffer);
        assertEquals(5, is.getSize());

        // Read partial byte array
        is.read(buffer, 0, 2);
        assertEquals(7, is.getSize());

        // Read remaining byte
        is.read();
        assertEquals(8, is.getSize());

        // Try to read past end
        int result = is.read();
        assertEquals(-1, result);
        assertEquals(8, is.getSize()); // Size shouldn't change when EOF is reached
    }
}