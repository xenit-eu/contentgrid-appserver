package com.contentgrid.appserver.query.engine.api.exception;

import com.contentgrid.appserver.domain.values.version.Version;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class UnsatisfiedVersionException extends QueryEngineException {
    @NonNull
    private final Version actualVersion;
    @NonNull
    private final Version requestedVersion;

    @Override
    public String getMessage() {
        return "Requested version %s can not be satisfied (actual version %s)".formatted(requestedVersion, actualVersion);
    }
}
