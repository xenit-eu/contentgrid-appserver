package com.contentgrid.appserver.infrastructure.impl.fs.zip;

import com.contentgrid.appserver.infrastructure.api.ArtifactEntry;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryReference;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryUnreadableException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ZipArtifactEntry implements ArtifactEntry {

    private final ArtifactEntryReference entryReference;
    private final Path zipPath;

    @Override
    public ArtifactEntryReference getEntryReference() {
        return entryReference;
    }

    @Override
    public InputStream getInputStream() throws ArtifactEntryUnreadableException {
        try {
            var zipFile = new ZipFile(zipPath.toFile());
            var entry = zipFile.getEntry(entryReference.getRelativePath());
            var stream = zipFile.getInputStream(entry);
            return new FilterInputStream(stream) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        zipFile.close();
                    }
                }
            };
        } catch (IOException e) {
            throw new ArtifactEntryUnreadableException(entryReference, e);
        }
    }
}
