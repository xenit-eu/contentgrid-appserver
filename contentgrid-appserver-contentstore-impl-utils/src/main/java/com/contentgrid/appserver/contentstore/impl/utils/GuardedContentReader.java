package com.contentgrid.appserver.contentstore.impl.utils;

import com.contentgrid.appserver.contentstore.api.ContentReader;
import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.UnreadableContentException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * A {@link ContentReader} implementation that guarantees that the {@link #getContentInputStream()} method is only called once
 */
@RequiredArgsConstructor
public class GuardedContentReader implements ContentReader {

    @NonNull
    private final ContentReader delegate;

    private final AtomicBoolean used = new AtomicBoolean();

    @Override
    public InputStream getContentInputStream() throws UnreadableContentException {
        if(used.compareAndSet(false, true)) {
            return delegate.getContentInputStream();
        }
        throw new IllegalStateException("Reader %s can only be used once".formatted(delegate.getDescription()));
    }

    @Override
    public ContentReference getReference() {
        return delegate.getReference();
    }

    @Override
    public long getContentSize() {
        return delegate.getContentSize();
    }

    @Override
    public String getDescription() {
        return delegate.getDescription();
    }
}
