package com.contentgrid.appserver.infrastructure.impl.fs.zip;

import com.contentgrid.appserver.infrastructure.api.ArtifactEntry;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryUnreadableException;
import com.contentgrid.appserver.infrastructure.api.ArtifactReference;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ZipArtifactEntry implements ArtifactEntry {

    private final ArtifactReference reference;
    private final Path zipPath;
    private final String entryName;

    @Override
    public InputStream getInputStream() throws ArtifactEntryUnreadableException {
        try {
            var zipFile = new ZipFile(zipPath.toFile());
            var entry = zipFile.getEntry(entryName);
            if (entry == null) {
                zipFile.close();
                throw new ArtifactEntryUnreadableException(reference, "entry not found: " + entryName);
            }
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
            throw new ArtifactEntryUnreadableException(reference, e);
        }
    }
}
