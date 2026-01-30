package com.contentgrid.appserver.domain.data.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CountingInputStreamTest {
    @Test
    void bytesRead() throws IOException {
        var testData = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 };
        var bais = new ByteArrayInputStream(testData);
        var is = new CountingInputStream(bais);

        // Read single byte
        is.read();

        // Read byte array
        byte[] buffer = new byte[4];
        is.read(buffer);

        // Read partial byte array
        is.read(buffer, 0, 2);

        // Read remaining byte
        is.read();
        assertEquals(8, is.getSize());

        // Try to read past end
        int result = is.read();
        assertEquals(-1, result);
        assertEquals(8, is.getSize()); // Size shouldn't change when EOF is reached
    }

    @ParameterizedTest
    @CsvSource({"0", "1", "7"})
    void notAtTheEnd(int bytesRead) throws IOException {
        var testData = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 };
        var bais = new ByteArrayInputStream(testData);
        var is = new CountingInputStream(bais);

        is.readNBytes(bytesRead);
        assertThrows(IOException.class, is::getSize);
    }
}