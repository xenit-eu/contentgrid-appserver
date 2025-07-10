package com.contentgrid.appserver.content.impl.utils;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Counts the number of bytes written to the delegate {@link OutputStream}
 */
public class WriteCountOutputStream extends FilterOutputStream {

    private final AtomicLong size = new AtomicLong();

    public WriteCountOutputStream(OutputStream out) {
        super(out);
    }

    @Override
    public void write(int b) throws IOException {
        super.write(b);
        size.incrementAndGet();
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
        size.addAndGet(len);
    }

    public long getSize() {
        return size.get();
    }
}
