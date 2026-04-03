package com.contentgrid.appserver.infrastructure.api;

/**
 * Thrown when an {@link Artifact} cannot be accessed (e.g. the backing file or archive cannot be opened).
 */
public class ArtifactException extends Exception {

    public ArtifactException(String message) {
        super(message);
    }

    public ArtifactException(Throwable cause) {
        super(cause);
    }
}
