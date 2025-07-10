package com.contentgrid.appserver.content.impl.fs;

import com.contentgrid.appserver.content.api.ContentReference;
import com.contentgrid.appserver.content.api.ContentWriter;
import com.contentgrid.appserver.content.api.UnwritableContentException;
import com.contentgrid.appserver.content.impl.utils.CloseCallbackOutputStream;
import com.contentgrid.appserver.content.impl.utils.WriteCountOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FileContentWriter implements ContentWriter {

    @NonNull
    @Getter
    private final ContentReference reference;

    @NonNull
    private final Path path;

    @Getter
    long contentSize = -1;

    @Override
    public OutputStream getContentOutputStream() throws UnwritableContentException {
        try {
            var countingStream = new WriteCountOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE_NEW));
            return new CloseCallbackOutputStream(countingStream, () -> contentSize = countingStream.getSize());
        } catch (IOException e) {
            throw new UnwritableContentException(reference, e);
        }
    }

    @Override
    public String getDescription() {
        return "File [%s]".formatted(reference);
    }
}
