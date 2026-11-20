package com.contentgrid.appserver.domain.values.exception;

import com.contentgrid.appserver.domain.values.version.Version;
import com.contentgrid.appserver.domain.values.version.VersionConstraint;
import lombok.Getter;
import lombok.NonNull;

@Getter
public class UnsatisfiedVersionException extends RuntimeException { //TODO ACC-2447
    @NonNull
    private final Version actualVersion;
    @NonNull
    private final VersionConstraint requestedVersion;

    public UnsatisfiedVersionException(
            @NonNull Version actualVersion,
            @NonNull VersionConstraint requestedVersion
    ) {
        super();
        this.actualVersion = actualVersion;
        this.requestedVersion = requestedVersion;
    }

    public UnsatisfiedVersionException(
            @NonNull Version actualVersion,
            @NonNull VersionConstraint requestedVersion,
            @NonNull Throwable cause
    ) {
        super(cause);
        this.actualVersion = actualVersion;
        this.requestedVersion = requestedVersion;
    }

    @Override
    public String getMessage() {
        return "Requested version constraint '%s' can not be satisfied (actual version %s)".formatted(requestedVersion,
                actualVersion);
    }
}
