package com.contentgrid.appserver.infrastructure.api;

import java.nio.file.Path;
import java.util.List;

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
     * @return the entry at that path
     * @throws ArtifactException if the artifact cannot be accessed
     */
    ArtifactEntry load(Path path) throws ArtifactException;

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
            public ArtifactEntry load(Path path) throws ArtifactException {
                return Artifact.this.load(subDir.resolve(path).normalize());
            }

            @Override
            public List<ArtifactEntry> loadAll(Path path) throws ArtifactException {
                return Artifact.this.loadAll(subDir.resolve(path).normalize());
            }
        };
    }
}
