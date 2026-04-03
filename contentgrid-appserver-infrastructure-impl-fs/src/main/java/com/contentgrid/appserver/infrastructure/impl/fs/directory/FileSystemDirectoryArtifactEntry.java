package com.contentgrid.appserver.infrastructure.impl.fs.directory;

import com.contentgrid.appserver.infrastructure.api.ArtifactEntry;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryUnreadableException;
import com.contentgrid.appserver.infrastructure.api.ArtifactReference;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FileSystemDirectoryArtifactEntry implements ArtifactEntry {

    private final ArtifactReference reference;
    private final Path path;

    @Override
    public InputStream getInputStream() throws ArtifactEntryUnreadableException {
        try {
            return Files.newInputStream(path);
        } catch (IOException e) {
            throw new ArtifactEntryUnreadableException(reference, e);
        }
    }
}
