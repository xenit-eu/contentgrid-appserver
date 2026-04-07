package com.contentgrid.appserver.infrastructure.impl.fs.directory;

import com.contentgrid.appserver.infrastructure.api.ArtifactEntry;
import com.contentgrid.appserver.infrastructure.api.Artifact;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryNotFoundException;
import com.contentgrid.appserver.infrastructure.api.ArtifactException;
import com.contentgrid.appserver.infrastructure.api.ArtifactReference;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FileSystemDirectoryArtifact implements Artifact {

    private final Path directory;

    @Override
    public ArtifactReference getReference() {
        return ArtifactReference.of(ArtifactReference.Scheme.FILE, directory.toAbsolutePath().toString());
    }

    @Override
    public ArtifactEntry load(String path) throws ArtifactException {
        var ref = getReference();
        var file = directory.resolve(path).normalize();
        if (!Files.exists(file)) {
            throw new ArtifactEntryNotFoundException(ref, path);
        }
        return new FileSystemDirectoryArtifactEntry(ref, file);
    }

    @Override
    public List<ArtifactEntry> loadAll(String path) throws ArtifactException {
        var ref = getReference();
        var dir = directory.resolve(path).normalize();
        var result = new ArrayList<ArtifactEntry>();
        if (Files.isDirectory(dir)) {
            try (var stream = Files.walk(dir)) {
                stream.filter(Files::isRegularFile)
                        .map(file -> new FileSystemDirectoryArtifactEntry(ref, file))
                        .forEach(result::add);
            } catch (IOException e) {
                throw new ArtifactException(ref, e);
            }
        } else if (Files.exists(dir)) {
            result.add(new FileSystemDirectoryArtifactEntry(ref, dir));
        }
        return result;
    }
}
