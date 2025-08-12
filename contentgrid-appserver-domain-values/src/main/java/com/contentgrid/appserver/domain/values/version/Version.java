package com.contentgrid.appserver.domain.values.version;

import lombok.NonNull;

/**
 * Version specification for an entity
 */
public sealed interface Version permits UnspecifiedVersion, ExactlyVersion {

    static Version unspecified() {
        return UnspecifiedVersion.INSTANCE;
    }

    static Version exactly(@NonNull String version) {
        return new ExactlyVersion(version);
    }

    /**
     * Checks that the other version complies with the constraints of this version specification
     * @param otherVersion The other version to check against this version specification
     * @return boolean indicating whether the other version satisfies this version specification
     */
    boolean isSatisfiedBy(@NonNull Version otherVersion);

}
