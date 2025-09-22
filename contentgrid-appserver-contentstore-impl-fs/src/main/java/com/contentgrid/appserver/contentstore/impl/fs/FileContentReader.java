package com.contentgrid.appserver.contentstore.impl.fs;

import com.contentgrid.appserver.contentstore.api.ContentReader;
import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.UnreadableContentException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.NonNull;

public class FileContentReader extends FileContentAccessor implements ContentReader {
    @NonNull
    Path path;

    public FileContentReader(Path path, ContentReference contentReference, long contentSize) {
        super(contentReference, contentSize);
        this.path = path;
    }

    @Override
    public InputStream getContentInputStream() throws UnreadableContentException {
        try {
            return Files.newInputStream(path);
        } catch (IOException e) {
            throw new UnreadableContentException(getReference(), e);
        }
    }

}
