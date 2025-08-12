package com.contentgrid.appserver.content.impl.utils;

import com.contentgrid.appserver.content.api.ContentReference;
import com.contentgrid.appserver.content.api.ContentWriter;
import com.contentgrid.appserver.content.api.UnwritableContentException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * A {@link ContentWriter} implementation that guarantees that {@link #getContentOutputStream()} is only called once,
 * and that {@link #getReference()} is only called after the stream has been closed
 */
@RequiredArgsConstructor
public class GuardedContentWriter implements ContentWriter {

    @NonNull
    private final ContentWriter delegate;

    private final AtomicBoolean used = new AtomicBoolean();
    private final AtomicBoolean completed = new AtomicBoolean();

    @Override
    public OutputStream getContentOutputStream() throws UnwritableContentException {
        if(used.compareAndSet(false, true)) {
            return new CloseCallbackOutputStream(delegate.getContentOutputStream(), () -> completed.set(true));
        }
        throw new IllegalStateException("Writer %s can only be used once".formatted(delegate.getDescription()));
    }

    @Override
    public ContentReference getReference() {
        if(!completed.get()) {
            throw new IllegalStateException("Reference can only be accessed after writer is closed");
        }
        return delegate.getReference();
    }

    @Override
    public long getContentSize() {
        if(!completed.get()) {
            throw new IllegalStateException("Content size can only be accessed after writer is closed");
        }
        return delegate.getContentSize();
    }

    @Override
    public String getDescription() {
        return delegate.getDescription();
    }
}
