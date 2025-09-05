package com.contentgrid.appserver.content.impl.utils.testing;

import com.contentgrid.appserver.content.api.ContentReader;
import com.contentgrid.appserver.content.api.ContentReference;
import com.contentgrid.appserver.content.api.UnreadableContentException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MockContentReader implements ContentReader {
    private final byte[] data;
    @Getter
    private final ContentReference reference;

    public MockContentReader(byte[] data) {
        this(data, ContentReference.of("mock-data"));
    }

    @Override
    public InputStream getContentInputStream() throws UnreadableContentException {
        if(data == null) {
            throw new UnreadableContentException(reference, "No data");
        }
        return new ByteArrayInputStream(data);
    }

    @Override
    public long getContentSize() {
        if(data == null) {
            return -1;
        }
        return data.length;
    }

    @Override
    public String getDescription() {
        return "Mock reader %s".formatted(reference);
    }
}
