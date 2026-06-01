package com.contentgrid.appserver.infrastructure.api;

import lombok.Getter;
import lombok.NonNull;

/**
 * Thrown when a specific entry within an {@link Artifact} cannot be read
 * (e.g. the entry does not exist, or the underlying stream cannot be opened).
 */
@Getter
public class ArtifactEntryUnreadableException extends Exception {

    /** The reference identifying the unreadable artifact entry. */
    @NonNull
    private final ArtifactEntryReference reference;

    /**
     * @param reference the unreadable artifact entry
     * @param message   a description of the failure
     */
    public ArtifactEntryUnreadableException(ArtifactEntryReference reference, String message) {
        super("Artifact entry " + reference + ": " + message);
        this.reference = reference;
    }

    /**
     * @param reference the unreadable artifact entry
     * @param cause     the underlying exception
     */
    public ArtifactEntryUnreadableException(ArtifactEntryReference reference, Throwable cause) {
        super("Artifact entry " + reference + ": " + cause.getMessage(), cause);
        this.reference = reference;
    }
}
