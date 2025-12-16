package com.contentgrid.appserver.contentstore.impl.utils;

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
    // some delegates' skip may not skip into unread data
    // allow case-by-case option to use skipNBytes on delegate
    private boolean useDelegateSkipN = false;
    private boolean hasSkipped = false;

    public SkippingInputStream(@NonNull InputStream delegate, long skipBytes, boolean useDelegateSkipN)
    {
        this(delegate, skipBytes);
        this.useDelegateSkipN = useDelegateSkipN;
    }

    private void ensureSkipped() throws IOException {
        if(!hasSkipped) {
            delegate.skipNBytes(skipBytes);
            hasSkipped = true;
        }
    }

    @Override
    public long skip(long n) throws IOException {
        if (n <= 0) {
            return 0;
        }
        ensureSkipped();
        if (useDelegateSkipN) {
            delegate.skipNBytes(n);
            return n;
        }
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
