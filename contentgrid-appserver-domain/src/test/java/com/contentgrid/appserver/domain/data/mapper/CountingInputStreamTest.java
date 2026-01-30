package com.contentgrid.appserver.domain.data.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
        assertThrows(IllegalStateException.class, is::getSize);
    }

    @Test
    void closedByteArrayInputStream() throws IOException {
        var testData = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 };
        var bais = new ByteArrayInputStream(testData);
        var is = new CountingInputStream(bais);

        is.readNBytes(4);
        is.close();
        assertEquals(4, is.getSize());

        // You can continue to read a ByteArrayInputStream
        is.readNBytes(2);
        assertEquals(6, is.getSize());
    }

    @ParameterizedTest
    @CsvSource({"0", "4", "8"})
    void closedFileInputStream(int bytesRead, @TempDir Path dir) throws IOException {
        // Create a temporary file to obtain an InputStream that can actually be closed.
        var path = Files.createFile(dir.resolve("inputstream.tmp"));
        var testData = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 };
        Files.write(path, testData);

        var fis = new FileInputStream(path.toFile());
        var is = new CountingInputStream(fis);

        is.readNBytes(bytesRead);
        is.close();
        assertEquals(bytesRead, is.getSize());

        // You can no longer read FileInputStream
        assertThrows(IOException.class, is::read);
        assertEquals(bytesRead, is.getSize());
    }

}