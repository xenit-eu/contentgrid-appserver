package com.contentgrid.appserver.infrastructure.api;

import java.util.List;

/**
 * A collection of named entries backed by a specific storage location (filesystem directory, ZIP archive, classpath, …).
 * <p>
 * The artifact's identity is expressed as an {@link ArtifactReference}.
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
    ArtifactEntry load(String path) throws ArtifactException;

    /**
     * Loads all entries at or under the given path within this artifact, recursively.
     *
     * @param path the root path inside the artifact; use an empty string for the artifact root
     * @return all entries found under that path
     * @throws ArtifactException if the artifact cannot be accessed
     */
    List<ArtifactEntry> loadAll(String path) throws ArtifactException;
}
