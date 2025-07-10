package com.contentgrid.appserver.content.impl.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PartialContentInputStreamTest {
    private static final byte[] FULL_DATA = "This is a test string".getBytes(StandardCharsets.UTF_8);
    private static final byte NUL = 0;

    public interface Testcases {

        void readFully() throws IOException;

        void skipsBytesBeforeStartOfRange() throws IOException;

        void skipsBytesIntoRange() throws IOException;

        void skipsBytesInsideRange() throws IOException;

        void skipsBytesOutsideRange() throws IOException;

        void skipsBytesAfterEndOfRange() throws IOException;
    }

    @Nested
    class RangeFromStart implements Testcases {
        InputStream inputStream;

        @BeforeEach
        void setupInputStream() {
            inputStream = PartialContentInputStream.fromContentRange(
                    new ByteArrayInputStream(FULL_DATA, 0, 4),
                    "bytes 0-3/"+FULL_DATA.length // bytes in the range description are *inclusive*
            );
        }

        @AfterEach
        void closeInputStream() throws IOException {
            inputStream.close();
        }

        @Test
        @Override
        public void readFully() throws IOException {
            var readData = new byte[FULL_DATA.length];
            Arrays.fill(readData, (byte)0xba); // Fill array to detect that it is properly filled with NUL bytes by the read function
            IOUtils.readFully(inputStream, readData);

            assertEquals(FULL_DATA[0], readData[0]);
            assertEquals(FULL_DATA[1], readData[1]);
            assertEquals(FULL_DATA[2], readData[2]);
            assertEquals(FULL_DATA[3], readData[3]);
            for(int i = 4; i < FULL_DATA.length; i++) {
                assertEquals(NUL, readData[i]);
            }

            // Stream is at EOF after reading all the bytes
            assertEquals(-1, inputStream.read());
        }

        @Override
        public void skipsBytesBeforeStartOfRange() throws IOException {
            // Nothing to test here; the range is at the beginning
        }

        @Test
        @Override
        public void skipsBytesIntoRange() throws IOException {
            inputStream.skipNBytes(2);

            var readData = new byte[6];
            Arrays.fill(readData, (byte)0xba); // Fill array to detect that it is properly filled with NUL bytes by the read function
            IOUtils.readFully(inputStream, readData);
            assertEquals(FULL_DATA[2], readData[0]);
            assertEquals(FULL_DATA[3], readData[1]);
            assertEquals(NUL, readData[2]);
        }

        @Test
        @Override
        public void skipsBytesInsideRange() throws IOException {
            assertEquals(FULL_DATA[0] & 0xff, inputStream.read());
            inputStream.skipNBytes(2); // Bytes 1 & 2 are skipped
            assertEquals(FULL_DATA[3] & 0xff, inputStream.read());
            assertEquals(NUL & 0xff, inputStream.read());
        }

        @Test
        @Override
        public void skipsBytesOutsideRange() throws IOException {
            assertEquals(FULL_DATA[0]&0xff, inputStream.read());
            inputStream.skipNBytes(FULL_DATA.length - 1); // Skip until past the end of the range; right up until the end of the data

            assertEquals(-1, inputStream.read()); // EOF
        }

        @Test
        @Override
        public void skipsBytesAfterEndOfRange() throws IOException {
            inputStream.skipNBytes(4); // Skip right up to the end of the range

            assertEquals(FULL_DATA.length-4, inputStream.skip(Long.MAX_VALUE)); // All the rest of the bytes can be skipped at once
        }
    }

    @Nested
    class RangeToEnd implements Testcases {
        InputStream inputStream;

        @BeforeEach
        void setupInputStream() {
            inputStream = PartialContentInputStream.fromContentRange(
                    new ByteArrayInputStream(FULL_DATA, 10, FULL_DATA.length-10),
                    "bytes 10-"+FULL_DATA.length+"/*"
            );
        }

        @AfterEach
        void closeInputStream() throws IOException {
            inputStream.close();
        }

        @Test
        @Override
        public void readFully() throws IOException {
            var readData = new byte[FULL_DATA.length];
            Arrays.fill(readData, (byte)0xba); // Fill array to detect that it is properly filled with NUL bytes by the read function
            IOUtils.readFully(inputStream, readData);

            for(int i = 0; i < 10; i++) {
                assertEquals(NUL, readData[i]);
            }
            for(int i = 10; i < FULL_DATA.length; i++) {
                assertEquals(FULL_DATA[i], readData[i]);
            }
            // Stream is at EOF after reading all the bytes
            assertEquals(-1, inputStream.read());

        }

        @Test
        @Override
        public void skipsBytesBeforeStartOfRange() throws IOException {
            assertEquals(5, inputStream.skip(5)); // Can skip bytes before start of range

            // Check that read data skips the 5 bytes that were skipped
            var readData = new byte[FULL_DATA.length-5];
            Arrays.fill(readData, (byte)0xba); // Fill array to detect that it is properly filled with NUL bytes by the read function
            IOUtils.readFully(inputStream, readData);
            assertEquals(NUL, readData[0]);
            assertEquals(NUL, readData[4]);
            for(int i = 5; i < readData.length; i++) {
                assertEquals(FULL_DATA[5+i], readData[i]);
            }

            assertEquals(-1, inputStream.read()); // EOF
        }

        @Test
        @Override
        public void skipsBytesIntoRange() throws IOException {
            inputStream.skipNBytes(15);

            var readData = new byte[6];
            IOUtils.readFully(inputStream, readData);
            for(int i = 0; i < 6; i++) {
                assertEquals(FULL_DATA[15+i], readData[i]);
            }
        }

        @Test
        @Override
        public void skipsBytesInsideRange() throws IOException {
            inputStream.skipNBytes(10); // Skip right up to the start of the range

            assertEquals(FULL_DATA[10]&0xff, inputStream.read());
            inputStream.skipNBytes(2); // bytes 11 & 12 are skipped
            assertEquals(FULL_DATA[13]&0xff, inputStream.read());
        }

        @Test
        @Override
        public void skipsBytesOutsideRange() throws IOException {
            inputStream.skipNBytes(10); // Skip right up to the start of the range
            assertEquals(FULL_DATA[10]&0xff, inputStream.read());
            inputStream.skipNBytes(FULL_DATA.length - 11); // Skip until the end of the range

            assertEquals(-1, inputStream.read()); // EOF
        }

        @Test
        @Override
        public void skipsBytesAfterEndOfRange() throws IOException {
            // Nothing to test here, our range goes right until the end
        }
    }

    @Nested
    class MiddleRange implements Testcases {
        InputStream inputStream;

        @BeforeEach
        void setupInputStream() {
            inputStream = PartialContentInputStream.fromContentRange(
                    new ByteArrayInputStream(FULL_DATA, 3, 4),
                    "bytes 3-6/"+FULL_DATA.length // bytes in the range description are *inclusive*
            );
        }

        @AfterEach
        void closeInputStream() throws IOException {
            inputStream.close();
        }

        @Test
        @Override
        public void readFully() throws IOException {
            var readData = new byte[FULL_DATA.length];
            Arrays.fill(readData, (byte)0xba); // Fill array to detect that it is properly filled with NUL bytes by the read function
            IOUtils.readFully(inputStream, readData);

            for(int i = 0; i < 3; i++) {
                assertEquals(NUL, readData[i]);
            }
            assertEquals(FULL_DATA[3], readData[3]);
            assertEquals(FULL_DATA[4], readData[4]);
            assertEquals(FULL_DATA[5], readData[5]);
            assertEquals(FULL_DATA[6], readData[6]);
            for(int i = 7; i < FULL_DATA.length; i++) {
                assertEquals(NUL, readData[i]);
            }

            // Stream is at EOF after reading all the bytes
            assertEquals(-1, inputStream.read());
        }

        @Test
        @Override
        public void skipsBytesBeforeStartOfRange() throws IOException {
            assertEquals(2, inputStream.skip(2)); // Can skip bytes before start of range

            // Check that read data skips the 2 bytes that were skipped
            var readData = new byte[6];
            Arrays.fill(readData, (byte)0xba); // Fill array to detect that it is properly filled with NUL bytes by the read function
            IOUtils.readFully(inputStream, readData);
            assertEquals(NUL, readData[0]);
            assertEquals(FULL_DATA[3], readData[1]);
            assertEquals(FULL_DATA[4], readData[2]);
            assertEquals(FULL_DATA[5], readData[3]);
            assertEquals(FULL_DATA[6], readData[4]);
            assertEquals(NUL, readData[5]);
        }

        @Test
        @Override
        public void skipsBytesIntoRange() throws IOException {
            inputStream.skipNBytes(4);

            var readData = new byte[6];
            Arrays.fill(readData, (byte)0xba); // Fill array to detect that it is properly filled with NUL bytes by the read function
            IOUtils.readFully(inputStream, readData);
            assertEquals(FULL_DATA[4], readData[0]);
            assertEquals(FULL_DATA[5], readData[1]);
            assertEquals(FULL_DATA[6], readData[2]);
            assertEquals(NUL, readData[3]);
        }

        @Test
        @Override
        public void skipsBytesInsideRange() throws IOException {
            inputStream.skipNBytes(3); // Skip right up to the start of the range

            assertEquals(FULL_DATA[3]&0xff, inputStream.read());
            inputStream.skipNBytes(2); // Bytes 4 & 5 are skipped
            assertEquals(FULL_DATA[6]&0xff, inputStream.read());
            assertEquals(NUL&0xff, inputStream.read());
        }

        @Test
        @Override
        public void skipsBytesOutsideRange() throws IOException {
            inputStream.skipNBytes(3); // Skip right up to the start of the range
            assertEquals(FULL_DATA[3]&0xff, inputStream.read());
            inputStream.skipNBytes(FULL_DATA.length - 4); // Skip until past the end of the range; right up until the end of the data

            assertEquals(-1, inputStream.read()); // EOF
        }

        @Test
        @Override
        public void skipsBytesAfterEndOfRange() throws IOException {
            inputStream.skipNBytes(7); // Skip right up to the end of the range

            assertEquals(FULL_DATA.length-7, inputStream.skip(Long.MAX_VALUE)); // All the rest of the bytes can be skipped at once
        }
    }
}