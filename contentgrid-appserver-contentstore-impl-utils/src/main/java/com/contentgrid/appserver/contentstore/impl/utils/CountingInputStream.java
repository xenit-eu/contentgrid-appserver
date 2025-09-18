package com.contentgrid.appserver.contentstore.impl.utils;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;
import lombok.NonNull;

/**
 * Counts the number of bytes read from the delegate {@link InputStream}
 */
public class CountingInputStream extends FilterInputStream {

    private final AtomicLong size = new AtomicLong();

    public CountingInputStream(@NonNull InputStream in) {
        super(in);
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b != -1) {
            size.incrementAndGet();
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int bytesRead = super.read(b, off, len);
        if (bytesRead > 0) {
            size.addAndGet(bytesRead);
        }
        return bytesRead;
    }

    public long getSize() {
        return size.get();
    }
}