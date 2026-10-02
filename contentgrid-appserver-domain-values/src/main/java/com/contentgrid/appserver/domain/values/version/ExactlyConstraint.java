package com.contentgrid.appserver.domain.values.version;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class ExactlyConstraint implements VersionConstraint {

    @NonNull
    ExactlyVersion requiredVersion;

    @Override
    public boolean isSatisfiedBy(@NonNull Version otherVersion) {
        return requiredVersion.isSatisfiedBy(otherVersion);
    }

    @Override
    public String toString() {
        return "exactly";
    }
}
