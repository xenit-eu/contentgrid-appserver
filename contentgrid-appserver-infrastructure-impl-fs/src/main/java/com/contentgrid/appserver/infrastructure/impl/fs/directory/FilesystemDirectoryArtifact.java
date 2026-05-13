package com.contentgrid.appserver.infrastructure.impl.fs.directory;

import com.contentgrid.appserver.infrastructure.api.ArtifactEntry;
import com.contentgrid.appserver.infrastructure.api.Artifact;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryReference;
import com.contentgrid.appserver.infrastructure.api.ArtifactException;
import com.contentgrid.appserver.infrastructure.api.ArtifactReference;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FilesystemDirectoryArtifact implements Artifact {

    public static final String SCHEME = "file";

    private final Path directory;

    @Override
    public ArtifactReference getReference() {
        return ArtifactReference.of(SCHEME + ":" + directory.toAbsolutePath());
    }

    @Override
    public Optional<ArtifactEntry> load(Path path) throws ArtifactException {
        var ref = ArtifactEntryReference.of(getReference(), path.toString());
        var file = directory.resolve(path).normalize();
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        return Optional.of(new FilesystemDirectoryArtifactEntry(ref, file));
    }

    @Override
    public List<ArtifactEntry> loadAll(Path path) throws ArtifactException {
        var ref = getReference();
        var dir = directory.resolve(path).normalize();
        if (Files.isDirectory(dir)) {
            try (var stream = Files.walk(dir)) {
                return stream.filter(Files::isRegularFile)
                        .map(file -> new FilesystemDirectoryArtifactEntry(
                                ArtifactEntryReference.of(ref, directory.relativize(file).toString()),
                                file))
                        .collect(Collectors.toList());
            } catch (IOException e) {
                throw new ArtifactException(ref, e);
            }
        } else if (Files.exists(dir)) {
            return List.of(new FilesystemDirectoryArtifactEntry(ArtifactEntryReference.of(ref, path.toString()), dir));
        }
        return List.of();
    }
}
