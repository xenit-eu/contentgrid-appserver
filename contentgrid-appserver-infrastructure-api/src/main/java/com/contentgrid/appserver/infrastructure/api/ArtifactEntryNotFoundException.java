package com.contentgrid.appserver.infrastructure.api;

import java.nio.file.Path;

/**
 * Thrown by {@link Artifact#loadRequired(Path)} when the requested path does not exist within the artifact.
 */
public class ArtifactEntryNotFoundException extends ArtifactEntryUnreadableException {

    /**
     * @param reference the artifact entry reference that was not found
     */
    public ArtifactEntryNotFoundException(ArtifactEntryReference reference) {
        super(reference, "not found");
    }
}
