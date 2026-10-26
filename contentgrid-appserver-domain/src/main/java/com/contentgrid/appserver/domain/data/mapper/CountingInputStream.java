package com.contentgrid.appserver.domain.data.mapper;

import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;
import lombok.NonNull;
import lombok.SneakyThrows;

/**
 * Counts the number of bytes read from the delegate {@link InputStream}
 */
class CountingInputStream extends FilterInputStream {

    private final AtomicLong size = new AtomicLong();
    private boolean closed = false;

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

    @Override
    public void close() throws IOException {
        super.close();
        closed = true;
    }

    // Sneaky throws, because the exception is thrown in a check for an invalid state; this doesn't have to be handled directly
    @SneakyThrows(IOException.class)
    public long getSize() {
        try {
            if (!closed && this.read() != -1) {
                throw new IllegalStateException("InputStream has not been fully read");
            }
        } catch (EOFException e) {
            // ignore
        }
        return size.get();
    }
}