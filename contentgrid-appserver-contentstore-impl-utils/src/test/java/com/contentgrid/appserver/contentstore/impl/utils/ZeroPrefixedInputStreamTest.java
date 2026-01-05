package com.contentgrid.appserver.contentstore.impl.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ZeroPrefixedInputStreamTest extends AbstractDelegatingInputStreamTest<ZeroPrefixedInputStream> {
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
    
    @Test
    void skipWithUnskippingDelegateAndPrefixSkip() throws Exception {
        // if no data was read yet (decrypted), the underlying cipher stream can not skip
        // skip is limited to the zero prefix
        try (var sis = new ZeroPrefixedInputStream(getUnskippableInputStream(), 5)) {
            assertEquals(5, sis.skip(10));
        }

        // if some data was read, the underlying cipher stream can only skip up to the block size
        // AES block size is 16 byte, test data is longer
        try (var sis = new ZeroPrefixedInputStream(getUnskippableInputStream(), 5)) {
            // can only skip as much as zero prefix, but not into actual content
            assertEquals(5, sis.skip(10));
            assertEquals(FULL_DATA[0], sis.read());
            // can skip until end of decrypted block
            assertEquals(15, sis.skip(20));
        }
    }

    @Test
    void skipWithSkippingDelegateAndPrefixSkip() throws Exception {
        // with us forcing skipNBytes on the delegate, everything should be fine
        // this is only safe unless trying to skip beyond the limit, due to implicit read
        try (var sis = new ZeroPrefixedInputStream(getProperSkippableInputStream(), 5)) {
            assertEquals(3, sis.skip(3));
            // this skips prefix and some real data
            assertEquals(3, sis.skip(3));
            assertEquals(10, sis.skip(10));
        }
    }

    protected ZeroPrefixedInputStream wrapDelegate(InputStream is) {
        return new ZeroPrefixedInputStream(is, 0);
    }
}