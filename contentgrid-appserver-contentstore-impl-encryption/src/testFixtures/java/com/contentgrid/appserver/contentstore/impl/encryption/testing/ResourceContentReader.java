package com.contentgrid.appserver.contentstore.impl.encryption.testing;

import com.contentgrid.appserver.contentstore.api.ContentReader;
import com.contentgrid.appserver.contentstore.api.ContentReference;

import java.io.InputStream;
import java.net.URL;
import lombok.SneakyThrows;

public class ResourceContentReader implements ContentReader
{

    private final URL resourceUrl;

    public ResourceContentReader(URL resourceUrl)
    {
        this.resourceUrl = resourceUrl;
    }

    @Override
    public ContentReference getReference()
    {
        return ContentReference.of(this.resourceUrl.toString());
    }

    @Override
    public String getDescription()
    {
        return "resource file " + this.resourceUrl.toString();
    }

    @Override
    @SneakyThrows
    public InputStream getContentInputStream()
    {
        return resourceUrl.openStream();
    }
}