package com.contentgrid.appserver.infrastructure.api;

import lombok.Getter;
import lombok.NonNull;

/**
 * Thrown when an {@link Artifact} cannot be accessed (e.g. the backing file or archive cannot be opened).
 */
@Getter
public class ArtifactException extends Exception {

    /** The reference identifying the artifact that could not be accessed. */
    @NonNull
    private final ArtifactReference reference;

    /**
     * @param reference the artifact that could not be accessed
     * @param message   a description of the failure
     */
    public ArtifactException(ArtifactReference reference, String message) {
        super("Artifact " + reference + ": " + message);
        this.reference = reference;
    }

    /**
     * @param reference the artifact that could not be accessed
     * @param cause     the underlying exception
     */
    public ArtifactException(ArtifactReference reference, Throwable cause) {
        super("Artifact " + reference + ": " + cause.getMessage(), cause);
        this.reference = reference;
    }
}
