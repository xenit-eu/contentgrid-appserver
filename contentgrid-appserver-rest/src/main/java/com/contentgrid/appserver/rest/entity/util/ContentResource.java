package com.contentgrid.appserver.rest.entity.util;


import com.contentgrid.appserver.domain.ContentApi;
import com.contentgrid.appserver.domain.ContentApi.Content;
import java.io.IOException;
import java.io.InputStream;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import org.springframework.core.io.AbstractResource;

@EqualsAndHashCode(callSuper = false)
public class ContentResource extends AbstractResource {
    @NonNull
    private final ContentApi.Content content;

    public ContentResource(Content content) {
        this.content = content;
    }

    @Override
    public long contentLength() {
        return content.getLength();
    }

    @Override
    public String getDescription() {
        return content.getDescription();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return content.getInputStream();
    }

    @Override
    public boolean exists() {
        return true;
    }
}
