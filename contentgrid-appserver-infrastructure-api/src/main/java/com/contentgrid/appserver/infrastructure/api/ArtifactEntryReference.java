package com.contentgrid.appserver.infrastructure.api;

import java.io.Serializable;
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
    String path;

    @Override
    public String toString() {
        var artifactRefString = artifactReference.toString();
        return artifactRefString + (artifactRefString.endsWith("/") ? "":"/") + path;
    }
}
