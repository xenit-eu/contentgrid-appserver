package com.contentgrid.appserver.infrastructure.api;

import java.io.Serializable;
import lombok.NonNull;
import lombok.Value;

/**
 * Identifies a {@link BlueprintArtifact}.
 * <p>
 * The string form is {@code scheme:path} (e.g. {@code file:/opt/app}, {@code zip:/opt/app.zip},
 * {@code classpath:config/defaults}). Use {@link #of(String)} to obtain an instance from that
 * representation and {@link #toString()} to convert back.
 */
@Value(staticConstructor = "of")
public class BlueprintArtifactReference implements Serializable {

    /** The reference value of the blueprint artifact. */
    @NonNull
    String value;

    @Override
    public String toString() {
        return value;
    }
}
