package com.contentgrid.appserver.infrastructure.api;

import java.nio.file.Path;

/**
 * Thrown by {@link BlueprintArtifact#loadRequired(Path)} when the requested path does not exist
 * within the {@link BlueprintArtifact}.
 */
public class BlueprintArtifactItemNotFoundException extends BlueprintArtifactItemUnreadableException {

    /**
     * @param reference the reference of the blueprint artifact item that was not found
     */
    public BlueprintArtifactItemNotFoundException(BlueprintArtifactItemReference reference) {
        super(reference, "not found");
    }
}
