package com.contentgrid.appserver.infrastructure.impl.fs.zip;

import com.contentgrid.appserver.infrastructure.api.AbstractRemoteArtifact;
import com.contentgrid.appserver.infrastructure.api.Artifact;
import com.contentgrid.appserver.infrastructure.api.ArtifactException;
import com.contentgrid.appserver.infrastructure.api.ArtifactReference;
import com.contentgrid.appserver.infrastructure.impl.fs.directory.FilesystemDirectoryArtifact;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ZipArtifact extends AbstractRemoteArtifact {

    private static final String TEMP_DIR_PREFIX = "zip-artifact-";
    public static final String SCHEME = "zip";

    private final Path zipPath;
    private Path tempDir;

    @Override
    public ArtifactReference getReference() {
        return ArtifactReference.of(SCHEME + ":" + zipPath.toAbsolutePath());
    }

    @Override
    protected Artifact createDelegate() throws ArtifactException {
        var reference = getReference();
        try (var zipFile = new ZipFile(zipPath.toFile())) {
            tempDir = Files.createTempDirectory(TEMP_DIR_PREFIX);
            zipFile.entries().asIterator().forEachRemaining(zipEntry -> {
                try {
                    if (!zipEntry.isDirectory()) {
                        var name = zipEntry.getName();
                        var directory = name.substring(0, name.lastIndexOf('/') + 1);
                        Files.createDirectories(tempDir.resolve(directory)); // create missing directories first
                        Files.write(tempDir.resolve(name), zipFile.getInputStream(zipEntry).readAllBytes());
                    }
                } catch (IOException e) {
                    // tempDir is not writable, or zipEntry is not readable
                    throw new WrappedIOException(e);
                }
            });
            return new FilesystemDirectoryArtifact(tempDir);
        } catch (IOException e) {
            throw new ArtifactException(reference, e);
        } catch (WrappedIOException e) {
            throw new ArtifactException(reference, e.unwrap());
        }
    }

    /**
     * An {@link IOException} wrapped in a {@link RuntimeException} so that it can be used inside lambda functions.
     */
    private static class WrappedIOException extends RuntimeException {

        public WrappedIOException(IOException cause) {
            super(cause);
        }

        public IOException unwrap() {
            return (IOException) getCause();
        }
    }
}
