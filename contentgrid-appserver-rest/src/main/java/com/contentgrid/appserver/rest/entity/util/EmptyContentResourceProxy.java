package com.contentgrid.appserver.rest.entity.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.Resource;

@RequiredArgsConstructor
public class EmptyContentResourceProxy implements Resource {
    @Delegate(types = Resource.class, excludes = InputStreamSource.class)
    @NonNull
    private final Resource delegate;

    @Override
    public InputStream getInputStream() throws IOException {
        return new ZeroInputStream(delegate.contentLength());
    }

    /**
     * InputStream that emulates the same length as the original content object, but filled with NUL-bytes instead of
     * the actual file contents.
     * <p>
     * We can't use {@link InputStream#nullInputStream()} instead, because that is a stream with a length of 0 bytes,
     * which doesn't match the size of the original input stream closely enough to make Content-Length and byte-ranges work properly
     */
    @RequiredArgsConstructor
    private static class ZeroInputStream extends InputStream {
        private final long size;
        private long currentPosition = 0;

        @Override
        public int read() {
            if(currentPosition < size) {
                currentPosition++;
                return 0;
            }
            return -1;
        }

        @Override
        public int available() {
            return (int)Math.min(Integer.MAX_VALUE, size - currentPosition);
        }

        @Override
        public int read(byte[] b, int off, int len) {
            Objects.checkFromIndexSize(off, len, b.length);
            if(currentPosition >= size) {
                return -1;
            }
            var skipped = (int)skip(len);
            for(int i = off; i<skipped;i++)
                b[i] = 0;
            return skipped;
        }

        @Override
        public long skip(long n) {
            if(n<=0) {
                return 0;
            }
            var toSkip = Math.min(n, available());
            currentPosition+=toSkip;
            return toSkip;
        }
    }
}
