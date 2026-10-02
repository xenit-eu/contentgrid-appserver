package com.contentgrid.appserver.domain.values.exception;

import com.contentgrid.appserver.application.model.exceptions.ApplicationModelException;
import com.contentgrid.appserver.domain.values.version.Version;
import com.contentgrid.appserver.domain.values.version.VersionConstraint;
import lombok.Getter;
import lombok.NonNull;

@Getter
public class UnsatisfiedVersionException extends ApplicationModelException {
    @NonNull
    private final Version actualVersion;
    @NonNull
    private final VersionConstraint requestedVersion;

    public UnsatisfiedVersionException(
            @NonNull Version actualVersion,
            @NonNull VersionConstraint requestedVersion
    ) {
        super("Requested version constraint '%s' can not be satisfied (actual version %s)".formatted(requestedVersion,
                actualVersion));
        this.actualVersion = actualVersion;
        this.requestedVersion = requestedVersion;
    }
}
