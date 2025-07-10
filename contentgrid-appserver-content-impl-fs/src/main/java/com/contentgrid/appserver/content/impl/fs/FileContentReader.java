package com.contentgrid.appserver.content.impl.fs;

import com.contentgrid.appserver.content.api.ContentReader;
import com.contentgrid.appserver.content.api.ContentReference;
import com.contentgrid.appserver.content.api.UnreadableContentException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

@AllArgsConstructor
public class FileContentReader implements ContentReader {

    @NonNull
    @Getter
    ContentReference reference;
    @NonNull
    Path path;

    @Getter
    long contentSize;

    @Override
    public InputStream getContentInputStream() throws UnreadableContentException {
        try {
            return Files.newInputStream(path);
        } catch (IOException e) {
            throw new UnreadableContentException(reference, e);
        }
    }

    @Override
    public String getDescription() {
        return "File [%s]".formatted(reference);
    }

}
