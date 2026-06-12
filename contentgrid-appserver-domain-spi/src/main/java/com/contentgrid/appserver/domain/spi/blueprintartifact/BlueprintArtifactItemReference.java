package com.contentgrid.appserver.domain.spi.blueprintartifact;

import java.io.Serializable;
import lombok.NonNull;
import lombok.Value;

/**
 * Identifies a single item within a {@link BlueprintArtifact}.
 * <p>
 * Combines the {@link BlueprintArtifactReference} of the containing blueprint artifact with the item's path
 * relative to that blueprint artifact's root.
 */
@Value(staticConstructor = "of")
public class BlueprintArtifactItemReference implements Serializable {

    /** The reference of the blueprint artifact that contains this item. */
    @NonNull
    BlueprintArtifactReference blueprintArtifactReference;

    /** The path of this item relative to the blueprint artifact root, including the filename. */
    @NonNull
    String path;

    @Override
    public String toString() {
        var blueprintArtifactRefString = blueprintArtifactReference.toString();
        return blueprintArtifactRefString + (blueprintArtifactRefString.endsWith("/") ? "":"/") + path;
    }
}
