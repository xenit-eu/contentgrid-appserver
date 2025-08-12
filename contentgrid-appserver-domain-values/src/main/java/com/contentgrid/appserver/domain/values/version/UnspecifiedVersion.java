package com.contentgrid.appserver.domain.values.version;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * Places no requirement on the object version.
 * <p>
 * This type is primarily use for requests to the query engine
 * It may also be returned in a response from the query engine in case that the object does not store version information
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UnspecifiedVersion implements Version {

    static final UnspecifiedVersion INSTANCE = new UnspecifiedVersion();

    @Override
    public String toString() {
        return "any version";
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Version otherVersion) {
        return true;
    }
}
