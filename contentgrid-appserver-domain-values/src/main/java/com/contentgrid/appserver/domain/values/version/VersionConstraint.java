package com.contentgrid.appserver.domain.values.version;

import lombok.NonNull;

public interface VersionConstraint {

    VersionConstraint ANY = new VersionConstraint() {
        @Override
        public boolean isSatisfiedBy(@NonNull Version otherVersion) {
            return true;
        }

        @Override
        public String toString() {
            return "any";
        }
    };

    /**
     * Checks that the other version complies with the constraints of this version specification
     * @param otherVersion The other version to check against this version specification
     * @return boolean indicating whether the other version satisfies this version specification
     */
    boolean isSatisfiedBy(@NonNull Version otherVersion);
}
