package com.contentgrid.appserver.infrastructure.api;

import java.io.Serializable;
import java.nio.file.Path;
import lombok.NonNull;
import lombok.Value;

/**
 * Identifies a single entry within an {@link Artifact}.
 * <p>
 * Combines the {@link ArtifactReference} of the containing artifact with the entry's path
 * relative to that artifact's root.
 */
@Value(staticConstructor = "of")
public class ArtifactEntryReference implements Serializable {

    /** The artifact that contains this entry. */
    @NonNull
    ArtifactReference artifactReference;

    /** The path of this entry relative to the artifact root, including the filename. */
    @NonNull
    String relativePath;

    /**
     * The absolute path to the entry, including the path to the artifact.
     * The path can point to a location that doesn't exist, e.g. path inside a zip archive.
     */
    public String getAbsolutePath() {
        return Path.of(artifactReference.getPath()).resolve(relativePath).toString();
    }

    /**
     * The filename of the entry.
     */
    public String getFilename() {
        return relativePath.substring(relativePath.lastIndexOf('/') + 1);
    }

    @Override
    public String toString() {
        return artifactReference.getScheme() + ":" + getAbsolutePath();
    }
}
