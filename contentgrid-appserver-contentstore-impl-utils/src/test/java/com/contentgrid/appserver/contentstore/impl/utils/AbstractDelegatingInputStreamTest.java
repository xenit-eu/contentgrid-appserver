package com.contentgrid.appserver.contentstore.impl.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;

import org.junit.jupiter.api.Test;

abstract class AbstractDelegatingInputStreamTest<T extends InputStream>
{
    protected static final byte[] FULL_DATA = "This is a test string".getBytes(StandardCharsets.UTF_8);

    @Test
    void skipNegative() throws IOException {
        try (InputStream is = wrapDelegate(new ByteArrayInputStream(FULL_DATA), false)) {
            assertEquals(0, is.skip(-100));
        }
    }

    @Test
    void skipNWithUnskippingDelegate() throws Exception {
        // if no data was read yet (decrypted), the underlying cipher stream can not skip
        try (var sis = wrapDelegate(getUnskippingInputStream(), false)) {
            assertEquals(0, sis.skip(5));
        }

        // if some data was read, the underlying cipher stream can only skip up to the block size
        // AES block size is 16 byte, test data is longer
        try (var sis = wrapDelegate(getUnskippingInputStream(), false)) {
            assertEquals(FULL_DATA[0], sis.read());
            assertEquals(5, sis.skip(5));
            assertEquals(10, sis.skip(12));
        }

        // with us forcing skipNBytes on the delegate, everything should be fine
        // this is only safe unless trying to skip beyond the limit, due to implicit read
        try (var sis = wrapDelegate(getUnskippingInputStream(), true)) {
            assertEquals(5, sis.skip(5));
            assertEquals(15, sis.skip(15));
        }
    }

    protected InputStream getUnskippingInputStream() throws Exception {
        var bos = new ByteArrayOutputStream();
        var keygen = KeyGenerator.getInstance("AES");
        var key = keygen.generateKey();
        var cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, key);

        try (var cos = new CipherOutputStream(bos, cipher)) {
            cos.write(FULL_DATA);
        }

        var bis = new ByteArrayInputStream(bos.toByteArray());
        cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, key);
        
        return new CipherInputStream(bis, cipher);
    }

    protected abstract T wrapDelegate(InputStream is, boolean useDelegateSkipN);
}
