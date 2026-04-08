package com.contentgrid.appserver.infrastructure.api;

import java.nio.file.Path;
import lombok.Getter;

/**
 * Thrown by {@link Artifact#load(Path)} when the requested path does not exist within the artifact.
 */
@Getter
public class ArtifactEntryNotFoundException extends ArtifactException {

    /** The path within the artifact that was requested but not found. */
    private final Path entryPath;

    /**
     * @param reference the artifact that was searched
     * @param entryPath the path within the artifact that was not found
     */
    public ArtifactEntryNotFoundException(ArtifactReference reference, Path entryPath) {
        super(reference, "entry not found: " + entryPath);
        this.entryPath = entryPath;
    }
}
