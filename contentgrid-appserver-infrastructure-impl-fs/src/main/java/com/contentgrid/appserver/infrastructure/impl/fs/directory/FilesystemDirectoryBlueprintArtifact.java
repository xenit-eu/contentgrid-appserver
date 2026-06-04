package com.contentgrid.appserver.infrastructure.impl.fs.directory;

import com.contentgrid.appserver.infrastructure.api.BlueprintArtifactItem;
import com.contentgrid.appserver.infrastructure.api.BlueprintArtifact;
import com.contentgrid.appserver.infrastructure.api.BlueprintArtifactItemReference;
import com.contentgrid.appserver.infrastructure.api.BlueprintArtifactException;
import com.contentgrid.appserver.infrastructure.api.BlueprintArtifactReference;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FilesystemDirectoryBlueprintArtifact implements BlueprintArtifact {

    public static final String SCHEME = "file";

    @NonNull
    private final Path directory;

    @Override
    public BlueprintArtifactReference getReference() {
        return BlueprintArtifactReference.of(SCHEME + ":" + directory.toAbsolutePath());
    }

    @Override
    public Optional<BlueprintArtifactItem> load(Path path) throws BlueprintArtifactException {
        var ref = BlueprintArtifactItemReference.of(getReference(), path.toString());
        var file = directory.resolve(path).normalize();
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        return Optional.of(new FilesystemDirectoryBlueprintArtifactItem(ref, file));
    }

    @Override
    public List<BlueprintArtifactItem> loadAll(Path path) throws BlueprintArtifactException {
        var ref = getReference();
        var dir = directory.resolve(path).normalize();
        if (Files.isDirectory(dir)) {
            try (var stream = Files.walk(dir)) {
                return stream.filter(Files::isRegularFile)
                        .map(file -> new FilesystemDirectoryBlueprintArtifactItem(
                                BlueprintArtifactItemReference.of(ref, directory.relativize(file).toString()),
                                file))
                        .collect(Collectors.toList());
            } catch (IOException e) {
                throw new BlueprintArtifactException(ref, e);
            }
        } else if (Files.exists(dir)) {
            return List.of(new FilesystemDirectoryBlueprintArtifactItem(BlueprintArtifactItemReference.of(ref, path.toString()), dir));
        }
        return List.of();
    }
}
