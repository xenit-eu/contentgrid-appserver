package com.contentgrid.appserver.infrastructure.api;

import java.io.Serializable;
import java.net.URI;
import lombok.NonNull;
import lombok.Value;

/**
 * Identifies an {@link Artifact} by scheme and path.
 * <p>
 * The string form is {@code scheme:path} (e.g. {@code file:/opt/app}, {@code zip:/opt/app.zip},
 * {@code classpath:config/defaults}). Use {@link #parse(String)} to obtain an instance from that
 * representation and {@link #toString()} to convert back.
 */
@Value(staticConstructor = "of")
public class ArtifactReference implements Serializable {

    /** The type of storage backing the artifact. */
    @NonNull
    String scheme;

    /** The location within the storage identified by {@link #scheme}. */
    @NonNull
    String path;

    /**
     * Parses a string of the form {@code scheme:path} into an {@link ArtifactReference}.
     *
     * @param value the string to parse
     * @return the parsed reference
     * @throws IllegalArgumentException if the scheme is unknown or the string is not a valid URI
     */
    public static ArtifactReference parse(String value) {
        var uri = URI.create(value);
        return of(uri.getScheme(), uri.getSchemeSpecificPart());
    }

    /** Returns the string form {@code scheme:path}. */
    @Override
    public String toString() {
        return scheme + ":" + path;
    }
}
