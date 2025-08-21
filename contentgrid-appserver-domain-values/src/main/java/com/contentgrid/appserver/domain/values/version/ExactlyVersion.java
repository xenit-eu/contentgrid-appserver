package com.contentgrid.appserver.domain.values.version;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * Places an exact requirement on the object version.
 * <p>
 * When used for requests to the query engine, it requires the object to be the specified version when performing the operation,
 * otherwise the request must be rejected.
 * When used in responses from the query engine, it indicates the version of the object at the time the operation was performed.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Value
public class ExactlyVersion implements Version {

    @NonNull
    String version;

    @Override
    public String toString() {
        return "exactly '%s'".formatted(version);
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Version otherVersion) {
        return otherVersion instanceof ExactlyVersion exactlyEntityVersion
                && Objects.equals(exactlyEntityVersion.getVersion(), version);
    }
}
