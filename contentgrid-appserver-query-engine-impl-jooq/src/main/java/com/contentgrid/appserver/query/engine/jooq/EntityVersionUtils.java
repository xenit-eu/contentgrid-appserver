package com.contentgrid.appserver.query.engine.jooq;

import com.contentgrid.appserver.domain.values.version.Version;
import lombok.experimental.UtilityClass;

@UtilityClass
public class EntityVersionUtils {
    static Version getVersion(Long versionData) {
        if(versionData == null) {
            // No data of the version field is returned, or the version field is null
            return Version.unspecified();
        }

        return Version.exactly(Long.toString(versionData, Character.MAX_RADIX));
    }

}
