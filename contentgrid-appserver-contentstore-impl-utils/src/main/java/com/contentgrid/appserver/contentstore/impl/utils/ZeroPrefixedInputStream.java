package com.contentgrid.appserver.contentstore.impl.utils;

import java.io.IOException;
import java.io.InputStream;

/**
 * Adds a fixed amount of 0-bytes in front of the delegate {@link InputStream}
 */
public class ZeroPrefixedInputStream extends InputStream {
    private final InputStream delegate;
    // some delegates' skip may not skip into unread data
    // allow case-by-case option to use skipNBytes on delegate
    private final boolean useDelegateSkipN;
    private long prefixBytes;

    public ZeroPrefixedInputStream(InputStream delegate, long prefixBytes) {
        this.delegate = delegate;
        this.prefixBytes = prefixBytes;
        this.useDelegateSkipN = false;
    }

    public ZeroPrefixedInputStream(InputStream delegate, long prefixBytes, boolean useDelegateSkipN) {
        this.delegate = delegate;
        this.prefixBytes = prefixBytes;
        this.useDelegateSkipN = useDelegateSkipN;
    }

    @Override
    public long skip(long n) throws IOException {
        if (n <= 0) {
            return 0;
        }
        if(n <= prefixBytes) {
            prefixBytes -= n;
            return n;
        }
        if(prefixBytes > 0) {
            n = n - prefixBytes; // Still skipping so many bytes from the offset
            try {
                if (useDelegateSkipN) {
                    delegate.skipNBytes(n);
                    return prefixBytes + n;
                }
                return prefixBytes + delegate.skip(n);
            } finally {
                prefixBytes = 0; // Now the whole offset is consumed; skip to the delegate
            }
        }

        if (useDelegateSkipN) {
            delegate.skipNBytes(n);
            return n;
        }
        return delegate.skip(n);
    }

    @Override
    public int read() throws IOException {
        if(prefixBytes > 0) {
            prefixBytes--;
            return 0;
        }
        return delegate.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if(prefixBytes > 0) {
            return super.read(b, off, len);
        }
        return delegate.read(b, off, len);
    }

    @Override
    public int available() throws IOException {
        if(prefixBytes > 0) {
            return (int)Math.max(prefixBytes, Integer.MAX_VALUE);
        }
        return delegate.available();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
