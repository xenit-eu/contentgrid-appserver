package com.contentgrid.appserver.query.engine.api.exception;

import com.contentgrid.appserver.domain.values.version.Version;
import com.contentgrid.appserver.domain.values.version.VersionConstraint;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class UnsatisfiedVersionException extends QueryEngineException {
    @NonNull
    private final Version actualVersion;
    @NonNull
    private final VersionConstraint requestedVersion;

    @Override
    public String getMessage() {
        return "Requested version constraint '%s' can not be satisfied (actual version %s)".formatted(requestedVersion, actualVersion);
    }
}
