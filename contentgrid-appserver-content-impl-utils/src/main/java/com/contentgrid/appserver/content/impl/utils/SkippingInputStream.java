package com.contentgrid.appserver.content.impl.utils;

import java.io.IOException;
import java.io.InputStream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Skips a certain amount of bytes from the delegate {@link InputStream}
 */
@RequiredArgsConstructor
public class SkippingInputStream extends InputStream {
    @NonNull
    private final InputStream delegate;
    private final long skipBytes;
    private boolean hasSkipped = false;

    private void ensureSkipped() throws IOException {
        if(!hasSkipped) {
            delegate.skipNBytes(skipBytes);
            hasSkipped = true;
        }
    }

    @Override
    public long skip(long n) throws IOException {
        ensureSkipped();
        return delegate.skip(n);
    }

    @Override
    public int read() throws IOException {
        ensureSkipped();
        return delegate.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        ensureSkipped();
        return delegate.read(b, off, len);
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
