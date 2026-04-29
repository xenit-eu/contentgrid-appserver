package com.contentgrid.appserver.infrastructure.api;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * A collection of named entries backed by a specific storage location (filesystem directory, ZIP archive, classpath, …).
 * <p>
 * The artifact's identity is expressed as an {@link ArtifactReference}, which can be used to reconstruct the artifact
 * via an {@link ArtifactReferenceResolver}.
 */
public interface Artifact {

    /**
     * Returns the reference that identifies this artifact.
     *
     * @return the artifact's reference
     */
    ArtifactReference getReference();

    /**
     * Loads a single entry at the given path within this artifact.
     *
     * @param path the path inside the artifact
     * @return an {@link Optional} containing the entry at the given path,
     * or an empty {@link Optional} if the entry does not exist
     * @throws ArtifactException if the artifact cannot be accessed
     */
    Optional<ArtifactEntry> load(Path path) throws ArtifactException;

    /**
     * Loads a single entry at the given path within this artifact.
     *
     * @param path the path inside the artifact
     * @return the entry at the given path
     * @throws ArtifactException if the artifact cannot be accessed
     * @throws ArtifactEntryNotFoundException if the entry does not exist
     */
    default ArtifactEntry loadRequired(Path path) throws ArtifactException {
        return load(path).orElseThrow(() -> new ArtifactEntryNotFoundException(getReference(), path.toString()));
    }

    /**
     * Loads all entries at or under the given path within this artifact, recursively.
     *
     * @param path the root path inside the artifact; use an empty string for the artifact root
     * @return all entries found under that path
     * @throws ArtifactException if the artifact cannot be accessed
     */
    List<ArtifactEntry> loadAll(Path path) throws ArtifactException;

    default Artifact subDir(Path subDir) {
        return new Artifact() {
            @Override
            public ArtifactReference getReference() {
                return Artifact.this.getReference();
            }

            @Override
            public Optional<ArtifactEntry> load(Path path) throws ArtifactException {
                return Artifact.this.load(subDir.resolve(path).normalize());
            }

            @Override
            public List<ArtifactEntry> loadAll(Path path) throws ArtifactException {
                return Artifact.this.loadAll(subDir.resolve(path).normalize());
            }
        };
    }
}
