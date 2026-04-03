package com.contentgrid.appserver.infrastructure.api;

import lombok.Getter;

/**
 * Thrown when a specific entry within an {@link Artifact} cannot be read
 * (e.g. the entry does not exist, or the underlying stream cannot be opened).
 */
@Getter
public class ArtifactEntryUnreadableException extends Exception {

    /** The reference identifying the artifact that contains the unreadable entry. */
    private final ArtifactReference reference;

    /**
     * @param reference the artifact containing the unreadable entry
     * @param message   a description of the failure
     */
    public ArtifactEntryUnreadableException(ArtifactReference reference, String message) {
        super("Artifact " + reference + ": " + message);
        this.reference = reference;
    }

    /**
     * @param reference the artifact containing the unreadable entry
     * @param cause     the underlying exception
     */
    public ArtifactEntryUnreadableException(ArtifactReference reference, Throwable cause) {
        super("Artifact " + reference + ": " + cause.getMessage(), cause);
        this.reference = reference;
    }
}
