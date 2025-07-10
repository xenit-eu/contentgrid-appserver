package com.contentgrid.appserver.content.impl.utils;

import com.contentgrid.appserver.content.api.ContentReader;
import com.contentgrid.appserver.content.api.ContentReference;
import com.contentgrid.appserver.content.api.UnreadableContentException;
import com.contentgrid.appserver.content.api.range.ResolvedContentRange;
import java.io.InputStream;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * A {@link ContentReader} implementation that emulates range support
 */
@RequiredArgsConstructor
public class EmulatedRangedContentReader implements ContentReader {

    @NonNull
    private final ContentReader delegate;

    @NonNull
    @Getter
    private final ResolvedContentRange range;

    @Override
    public InputStream getContentInputStream() throws UnreadableContentException {
        return PartialContentInputStream.fromContentRange(
                new SkippingInputStream(
                        delegate.getContentInputStream(),
                        range.getStartByte()
                ),
                range
        );
    }

    @Override
    public ContentReference getReference() {
        return delegate.getReference();
    }

    @Override
    public long getContentSize() {
        return range.getContentSize();
    }

    @Override
    public String getDescription() {
        return "Range [%s] of %s".formatted(range, delegate.getDescription());
    }
}
