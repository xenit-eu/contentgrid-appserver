package com.contentgrid.appserver.infrastructure.impl.fs.directory;

import com.contentgrid.appserver.infrastructure.api.ArtifactEntry;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryReference;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryUnreadableException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FileSystemDirectoryArtifactEntry implements ArtifactEntry {

    private final ArtifactEntryReference entryReference;
    private final Path absolutePath;

    @Override
    public ArtifactEntryReference getEntryReference() {
        return entryReference;
    }

    @Override
    public InputStream getInputStream() throws ArtifactEntryUnreadableException {
        try {
            return Files.newInputStream(absolutePath);
        } catch (IOException e) {
            throw new ArtifactEntryUnreadableException(entryReference, e);
        }
    }
}
