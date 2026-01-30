package com.contentgrid.appserver.domain.data.mapper;

import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;
import lombok.NonNull;

/**
 * Counts the number of bytes read from the delegate {@link InputStream}
 */
class CountingInputStream extends FilterInputStream {

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

    public long getSize() throws IOException {
        try {
            if (this.read() != -1) {
                throw new IOException("InputStream has not been fully read");
            }
        } catch (EOFException e) {
            // ignore
        }
        return size.get();
    }
}