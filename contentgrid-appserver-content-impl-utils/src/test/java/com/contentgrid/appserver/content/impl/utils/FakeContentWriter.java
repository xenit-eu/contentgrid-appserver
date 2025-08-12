package com.contentgrid.appserver.content.impl.utils;

import com.contentgrid.appserver.content.api.ContentReference;
import com.contentgrid.appserver.content.api.ContentWriter;
import java.io.OutputStream;
import org.apache.commons.io.output.NullOutputStream;

public class FakeContentWriter implements ContentWriter {

    @Override
    public OutputStream getContentOutputStream() {
        return NullOutputStream.INSTANCE;
    }

    @Override
    public ContentReference getReference() {
        return ContentReference.of("never");
    }

    @Override
    public long getContentSize() {
        return 0;
    }

    @Override
    public String getDescription() {
        return "Fake";
    }
}
