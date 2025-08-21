package com.contentgrid.appserver.domain.values.version;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Places a 'non-existing' requirement on the entity version.
 * <p>
 * When used for requests to the query engine, it requires the entity to not exist, otherwise the request must be rejected.
 * When used in responses from the query engine, it indicates that the entity does not exist.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class NonExistingVersion implements Version {
    static final Version INSTANCE = new NonExistingVersion();

    @Override
    public boolean isSatisfiedBy(@NonNull Version otherVersion) {
        return otherVersion == INSTANCE;
    }

    @Override
    public String toString() {
        return "non existing";
    }
}
