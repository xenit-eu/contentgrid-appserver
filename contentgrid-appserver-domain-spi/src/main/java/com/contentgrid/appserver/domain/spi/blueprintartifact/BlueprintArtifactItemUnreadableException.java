package com.contentgrid.appserver.domain.spi.blueprintartifact;

import lombok.Getter;
import lombok.NonNull;

/**
 * Thrown when a specific item within a {@link BlueprintArtifact} cannot be read
 * (e.g. the item does not exist, or the underlying stream cannot be opened).
 */
@Getter
public class BlueprintArtifactItemUnreadableException extends Exception {

    /** The reference identifying the unreadable blueprint artifact item. */
    @NonNull
    private final BlueprintArtifactItemReference reference;

    /**
     * @param reference the unreadable blueprint artifact item
     * @param message   a description of the failure
     */
    public BlueprintArtifactItemUnreadableException(BlueprintArtifactItemReference reference, String message) {
        super("Blueprint artifact item " + reference + ": " + message);
        this.reference = reference;
    }

    /**
     * @param reference the unreadable blueprint artifact item
     * @param cause     the underlying exception
     */
    public BlueprintArtifactItemUnreadableException(BlueprintArtifactItemReference reference, Throwable cause) {
        super("Blueprint artifact item " + reference + ": " + cause.getMessage(), cause);
        this.reference = reference;
    }
}
