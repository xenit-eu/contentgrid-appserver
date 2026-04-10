package com.contentgrid.appserver.infrastructure.api;

/**
 * Resolves an {@link ArtifactReference} into a concrete {@link Artifact}.
 */
public interface ArtifactReferenceResolver {

    /**
     * Returns the {@link Artifact} identified by the given reference.
     *
     * @param reference the reference to resolve
     * @return the resolved artifact
     */
    Artifact resolve(ArtifactReference reference);
}
