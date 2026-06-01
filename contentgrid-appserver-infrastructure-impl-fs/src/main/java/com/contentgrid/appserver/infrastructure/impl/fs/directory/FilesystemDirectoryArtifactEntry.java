package com.contentgrid.appserver.infrastructure.impl.fs.directory;

import com.contentgrid.appserver.infrastructure.api.ArtifactEntry;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryReference;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryUnreadableException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FilesystemDirectoryArtifactEntry implements ArtifactEntry {

    @Getter
    @NonNull
    private final ArtifactEntryReference entryReference;
    @NonNull
    private final Path absolutePath;

    @Override
    public InputStream getInputStream() throws ArtifactEntryUnreadableException {
        try {
            return Files.newInputStream(absolutePath);
        } catch (IOException e) {
            throw new ArtifactEntryUnreadableException(entryReference, e);
        }
    }
}
