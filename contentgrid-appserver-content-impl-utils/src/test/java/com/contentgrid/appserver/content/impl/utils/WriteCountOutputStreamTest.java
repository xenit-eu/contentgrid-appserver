package com.contentgrid.appserver.content.impl.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class WriteCountOutputStreamTest {
    @Test
    void bytesWritten() throws IOException {
        var baos = new ByteArrayOutputStream();
        var os = new WriteCountOutputStream(baos);

        os.write(4);

        assertEquals(baos.size(), os.getSize());

        os.write(new byte[] { 1, 2, 3, 4});

        assertEquals(baos.size(), os.getSize());

        os.write(new byte[] {1, 2, 3, 4}, 2, 1);

        assertEquals(baos.size(), os.getSize());
    }

}