package com.contentgrid.appserver.infrastructure.impl.fs.zip;

import com.contentgrid.appserver.infrastructure.api.Artifact;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntry;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryReference;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryUnreadableException;
import com.contentgrid.appserver.infrastructure.api.ArtifactException;
import com.contentgrid.appserver.infrastructure.api.ArtifactReference;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * {@link ZipArtifact} is an {@link Artifact} that opens a {@link ZipFile} and keeps it in memory
 * <ul>
 *   <li>{@link ZipArtifact#load(Path)} and {@link ZipArtifact#loadAll(Path)} create {@link ArtifactEntry}(-ies)
 *     that contain a {@link ZipEntry}
 *   <li>{@link ArtifactEntry#getInputStream()} can only be called once,
 *     calling it a second time throws {@link ArtifactEntryUnreadableException}
 *   <li>If {@link InputStream#close()} gets called, the {@link ArtifactEntry} will be closed as well.
 *     Which reduces {@code openEntries} in {@link ZipArtifact}
 *   <li>After {@link ZipArtifact#close()} gets called, {@link ZipArtifact#load(Path)} and {@link ZipArtifact#loadAll(Path)} throw,
 *     {@link ZipFile} is not closed immediately
 *   <li>The {@link ZipFile} in {@link ZipArtifact} will be closed whenever {@code openEntries} in {@link ZipArtifact}
 *     reaches {@code 0} after {@link ZipArtifact#close()} was called
 * </ul>
 */
public class ZipArtifact implements Artifact {

    public static final String SCHEME = "zip";

    private final ZipFile zipFile;
    private volatile boolean closed = false;
    private final AtomicInteger openEntries = new AtomicInteger(0);

    public ZipArtifact(Path zipPath) throws IOException {
        this.zipFile = new ZipFile(zipPath.toFile());
    }

    @Override
    public ArtifactReference getReference() {
        return ArtifactReference.of(SCHEME, zipFile.getName());
    }

    private void checkOpen() throws ArtifactException {
        if (closed) {
            throw new ArtifactException(getReference(), "artifact has been closed");
        }
    }

    @Override
    public Optional<ArtifactEntry> load(Path path) throws ArtifactException {
        checkOpen();
        var entry = zipFile.getEntry(path.toString());
        if (entry == null) {
            return Optional.empty();
        }
        return Optional.of(getArtifactEntry(entry));
    }

    @Override
    public List<ArtifactEntry> loadAll(Path path) throws ArtifactException {
        checkOpen();
        var prefix = path.normalize();
        var result = new ArrayList<ArtifactEntry>();
        zipFile.entries().asIterator().forEachRemaining(entry -> {
            var entryPath = Path.of(entry.getName());
            if ((prefix.toString().isEmpty() || entryPath.startsWith(prefix)) && !entry.isDirectory()) {
                result.add(getArtifactEntry(entry));
            }
        });
        return result;
    }

    @Override
    public void close() throws IOException {
        closed = true;
        if (openEntries.get() == 0) {
            zipFile.close();
        }
    }

    private void closeEntry() throws IOException {
        if (openEntries.decrementAndGet() == 0 && closed) {
            zipFile.close();
        }
    }

    private ArtifactEntry getArtifactEntry(ZipEntry zipEntry) {
        // Increment openEntries now and decrement openEntries after the inputStream gets closed
        var entryReference = ArtifactEntryReference.of(getReference(), zipEntry.getName());
        openEntries.incrementAndGet();
        return new ArtifactEntry() {
            // Make sure getInputStream() can only be called once
            // This allows us to close the ArtifactEntry itself if the inputStream gets closed
            private volatile boolean consumed = false;

            @Override
            public ArtifactEntryReference getEntryReference() {
                return entryReference;
            }

            @Override
            public synchronized InputStream getInputStream() throws ArtifactEntryUnreadableException {
                if (consumed) {
                    throw new ArtifactEntryUnreadableException(entryReference, "entry has already been consumed");
                }
                consumed = true;
                try {
                    var stream = zipFile.getInputStream(zipEntry);
                    return new FilterInputStream(stream) {
                        @Override
                        public void close() throws IOException {
                            try {
                                super.close();
                            } finally {
                                closeEntry();
                            }
                        }
                    };
                } catch (IOException e) {
                    try {
                        closeEntry();
                    } catch (IOException ignored) {}
                    throw new ArtifactEntryUnreadableException(entryReference, e);
                }
            }
        };
    }
}
