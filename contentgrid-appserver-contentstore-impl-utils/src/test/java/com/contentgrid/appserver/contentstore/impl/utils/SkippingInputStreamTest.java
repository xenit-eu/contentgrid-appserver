package com.contentgrid.appserver.contentstore.impl.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SkippingInputStreamTest extends AbstractDelegatingInputStreamTest<SkippingInputStream> {

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
    
    @Test
    void skipWithUnskippingDelegateAndPrefixSkip() throws Exception {
        // if some data was read, the underlying cipher stream can only skip up to the block size
        // AES block size is 16 byte, test data is longer
        // SkippingInputStream#ensureSkipped uses skipNBytes to read during initial skip
        try (var sis = new SkippingInputStream(getUnskippableInputStream(), 5)) {
            assertEquals(5, sis.skip(5));
            // reaching 16 byte block size and not going beyond
            assertEquals(6, sis.skip(10));
        }
    }

    @Test
    void skipWithSkippingDelegateAndPrefixSkip() throws Exception {
        // with us forcing skipNBytes on the delegate, everything should be fine
        // this is only safe unless trying to skip beyond the limit, due to implicit read
        try (var sis = new SkippingInputStream(getProperSkippableInputStream(), 5)) {
            assertEquals(5, sis.skip(5));
            assertEquals(10, sis.skip(10));
        }
    }

    protected SkippingInputStream wrapDelegate(InputStream is) {
        return new SkippingInputStream(is, 0);
    }
}