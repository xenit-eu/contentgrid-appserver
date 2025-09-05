package com.contentgrid.appserver.content.impl.encryption;

import com.contentgrid.appserver.content.api.ContentReference;
import com.contentgrid.appserver.content.api.ContentWriter;
import com.contentgrid.appserver.content.api.UnwritableContentException;
import com.contentgrid.appserver.content.impl.utils.CloseCallbackOutputStream;
import java.io.OutputStream;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class EncryptingContentWriter implements ContentWriter {
    private final ContentWriter delegate;
    private final Function<OutputStream, OutputStream> applyEncryption;
    private final Consumer<ContentReference> afterCompletion;

    @Override
    public OutputStream getContentOutputStream() throws UnwritableContentException {
        return new CloseCallbackOutputStream(
                applyEncryption.apply(delegate.getContentOutputStream()),
                () -> afterCompletion.accept(getReference())
        );
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
        return "Encrypted "+delegate.getDescription();
    }
}
