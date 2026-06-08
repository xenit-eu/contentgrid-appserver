package com.contentgrid.appserver.domain.spi.blueprintartifact;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * A collection of named items backed by a specific storage location (filesystem directory, ZIP archive, classpath, …).
 * <p>
 * The blueprint artifact's identity is expressed as a {@link BlueprintArtifactReference},
 * which can be used to reconstruct the blueprint artifact via a {@link BlueprintArtifactReferenceResolver}.
 */
public interface BlueprintArtifact {

    /**
     * Returns the reference that identifies this blueprint artifact.
     *
     * @return the blueprint artifact's reference
     */
    BlueprintArtifactReference getReference();

    /**
     * Loads a single item at the given path within this blueprint artifact.
     *
     * @param path the path inside the blueprint artifact
     * @return an {@link Optional} containing the item at the given path,
     * or an empty {@link Optional} if the item does not exist
     * @throws BlueprintArtifactException if the blueprint artifact cannot be accessed
     */
    Optional<BlueprintArtifactItem> load(Path path) throws BlueprintArtifactException;

    /**
     * Loads a single item at the given path within this blueprint artifact.
     *
     * @param path the path inside the blueprint artifact
     * @return the item at the given path
     * @throws BlueprintArtifactException if the blueprint artifact cannot be accessed
     * @throws BlueprintArtifactItemNotFoundException if the item does not exist
     */
    default BlueprintArtifactItem loadRequired(Path path) throws BlueprintArtifactException, BlueprintArtifactItemNotFoundException {
        return load(path).orElseThrow(() -> new BlueprintArtifactItemNotFoundException(
                BlueprintArtifactItemReference.of(getReference(), path.toString())));
    }

    /**
     * Loads all items at or under the given path within this blueprint artifact, recursively.
     *
     * @param path the root path inside the blueprint artifact; use an empty string for the blueprint artifact root
     * @return all items found under that path
     * @throws BlueprintArtifactException if the blueprint artifact cannot be accessed
     */
    List<BlueprintArtifactItem> loadAll(Path path) throws BlueprintArtifactException;

    default BlueprintArtifact subDir(@lombok.NonNull Path subDir) {
        return new BlueprintArtifact() {
            @Override
            public BlueprintArtifactReference getReference() {
                return BlueprintArtifact.this.getReference();
            }

            @Override
            public Optional<BlueprintArtifactItem> load(Path path) throws BlueprintArtifactException {
                return BlueprintArtifact.this.load(subDir.resolve(path).normalize());
            }

            @Override
            public List<BlueprintArtifactItem> loadAll(Path path) throws BlueprintArtifactException {
                return BlueprintArtifact.this.loadAll(subDir.resolve(path).normalize());
            }
        };
    }
}
