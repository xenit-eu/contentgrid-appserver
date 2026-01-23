package com.contentgrid.appserver.contentstore.impl.encryption.testing;

import com.contentgrid.appserver.contentstore.api.ContentReader;
import com.contentgrid.appserver.contentstore.api.ContentReference;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import lombok.SneakyThrows;

public class ResourceContentReader implements ContentReader
{

    private final URL resourceUrl;

    private final long contentSize;

    public ResourceContentReader(URL resourceUrl)
    {
        this.resourceUrl = resourceUrl;
        long size = 0;
        try (InputStream is = getContentInputStream())
        {
            byte[] buf = new byte[1024];
            int bytesRead = 0;
            while ((bytesRead = is.read(buf)) != -1)
            {
                size += bytesRead;
            }
        }
        catch (IOException ioex)
        {
            throw new RuntimeException(ioex);
        }
        this.contentSize = size;
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
    public long getContentSize()
    {
        return this.contentSize;
    }

    @Override
    @SneakyThrows
    public InputStream getContentInputStream()
    {
        return resourceUrl.openStream();
    }
}