package com.contentgrid.appserver.contentstore.impl.encryption.testing;

import com.contentgrid.appserver.contentstore.api.ContentReader;
import com.contentgrid.appserver.contentstore.api.ContentReference;

import java.io.IOException;
import java.io.InputStream;

public class ResourceContentReader implements ContentReader
{

    private final String resourceName;

    private final long contentSize;

    public ResourceContentReader(String resourceName)
    {
        this.resourceName = resourceName;
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
        return ContentReference.of(this.resourceName);
    }

    @Override
    public String getDescription()
    {
        return "resource file " + this.resourceName;
    }

    @Override
    public long getContentSize()
    {
        return this.contentSize;
    }

    @Override
    public InputStream getContentInputStream()
    {
        return ResourceContentReader.class.getResourceAsStream(resourceName);
    }
}