package com.contentgrid.appserver.infrastructure.api;

/**
 * Resolves a {@link BlueprintArtifactReference} into a concrete {@link BlueprintArtifact}.
 */
public interface BlueprintArtifactReferenceResolver {

    /**
     * Returns the {@link BlueprintArtifact} identified by the given reference.
     *
     * @param reference the reference to resolve
     * @return the resolved blueprint artifact
     */
    BlueprintArtifact resolve(BlueprintArtifactReference reference);
}
