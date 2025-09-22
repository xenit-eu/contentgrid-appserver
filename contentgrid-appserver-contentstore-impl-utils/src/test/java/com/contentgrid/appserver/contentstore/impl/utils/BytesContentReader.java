package com.contentgrid.appserver.contentstore.impl.utils;

import com.contentgrid.appserver.contentstore.api.ContentReader;
import com.contentgrid.appserver.contentstore.api.ContentReference;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class BytesContentReader implements ContentReader {
    private final byte[] bytes;

    @Override
    public InputStream getContentInputStream() {
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public ContentReference getReference() {
        return null;
    }

    @Override
    public long getContentSize() {
        return bytes.length;
    }

    @Override
    public String getDescription() {
        return "%d Bytes".formatted(getContentSize());
    }
}
