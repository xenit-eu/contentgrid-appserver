package com.contentgrid.appserver.infrastructure.impl.fs.zip;

import com.contentgrid.appserver.infrastructure.api.ArtifactEntry;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryReference;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryUnreadableException;
import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ZipArtifactEntry implements ArtifactEntry {

    @Getter
    private final ArtifactEntryReference entryReference;
    private final Path zipPath;

    @Override
    public InputStream getInputStream() throws ArtifactEntryUnreadableException {
        try {
            var zipFile = new ZipFile(zipPath.toFile());
            try (var holder = new DetachableCloseable(zipFile)) {
                var entry = zipFile.getEntry(entryReference.getRelativePath());
                var stream = zipFile.getInputStream(entry);
                var result = new FilterInputStream(stream) {
                    @Override
                    public void close() throws IOException {
                        try {
                            super.close();
                        } finally {
                            zipFile.close();
                        }
                    }
                };
                holder.detach();
                return result;
            }
        } catch (IOException e) {
            throw new ArtifactEntryUnreadableException(entryReference, e);
        }
    }

    // Making sure that zipFile gets closed when something goes wrong before returning
    private static class DetachableCloseable implements Closeable {
        private final Closeable delegate;
        private boolean detached;

        DetachableCloseable(Closeable delegate) {
            this.delegate = delegate;
        }

        void detach() {
            this.detached = true;
        }

        @Override
        public void close() throws IOException {
            if (!detached) {
                delegate.close();
            }
        }
    }
}
