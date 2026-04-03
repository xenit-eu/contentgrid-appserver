package com.contentgrid.appserver.infrastructure.api;

/**
 * Thrown when a specific entry within an {@link Artifact} cannot be read
 * (e.g. the entry does not exist, or the underlying stream cannot be opened).
 */
public class ArtifactEntryUnreadableException extends Exception {

    public ArtifactEntryUnreadableException(String message) {
        super(message);
    }

    public ArtifactEntryUnreadableException(Throwable cause) {
        super(cause);
    }
}
