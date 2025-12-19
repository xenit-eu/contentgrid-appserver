package com.contentgrid.appserver.contentstore.impl.utils;

import java.io.IOException;
import java.io.InputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;

public class SkippableCipherInputStream extends CipherInputStream
{

    public SkippableCipherInputStream(InputStream is, Cipher c)
    {
        super(is, c);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long skip(long n) throws IOException
    {
        var skipped = super.skip(n);
        var remaining = n - skipped;
        var eof = false;
        while (!eof && remaining > 0) {
            if (read() == -1) {
                eof = true;
            } else {
                skipped += 1;
                remaining = n - skipped;
            }
            if (!eof && remaining > 0) {
                skipped += super.skip(remaining);
                remaining = n - skipped;
            }
        }

        return skipped;
    }

    
}
