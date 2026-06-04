package com.contentgrid.appserver.infrastructure.api;

import lombok.Getter;
import lombok.NonNull;

/**
 * Thrown when a {@link BlueprintArtifact} cannot be accessed (e.g. the backing file or archive cannot be opened).
 */
@Getter
public class BlueprintArtifactException extends Exception {

    /** The reference identifying the blueprint artifact that could not be accessed. */
    @NonNull
    private final BlueprintArtifactReference reference;

    /**
     * @param reference the reference of the blueprint artifact that could not be accessed
     * @param message   a description of the failure
     */
    public BlueprintArtifactException(BlueprintArtifactReference reference, String message) {
        super("Blueprint artifact " + reference + ": " + message);
        this.reference = reference;
    }

    /**
     * @param reference the reference of the blueprint artifact that could not be accessed
     * @param cause     the underlying exception
     */
    public BlueprintArtifactException(BlueprintArtifactReference reference, Throwable cause) {
        super("Blueprint artifact " + reference + ": " + cause.getMessage(), cause);
        this.reference = reference;
    }
}
