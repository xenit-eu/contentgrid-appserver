package com.contentgrid.appserver.domain.values.version;

import lombok.NonNull;

/**
 * Version specification for an object
 */
public sealed interface Version extends VersionConstraint permits UnspecifiedVersion, ExactlyVersion {

    static Version unspecified() {
        return UnspecifiedVersion.INSTANCE;
    }

    static Version exactly(@NonNull String version) {
        return new ExactlyVersion(version);
    }

}
