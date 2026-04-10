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
     */
    public String getAbsolutePath() {
        return switch (artifactReference.getScheme()) {
            case FILE, CLASSPATH -> Path.of(artifactReference.getPath()).resolve(relativePath).toString();
            case ZIP -> artifactReference.getPath() + '!' + relativePath;
        };
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
