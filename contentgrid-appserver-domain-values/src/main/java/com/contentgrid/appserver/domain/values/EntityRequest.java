package com.contentgrid.appserver.domain.values;

import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.domain.values.version.Version;
import com.contentgrid.appserver.domain.values.version.VersionConstraint;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.With;

@Value
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class EntityRequest {

    @NonNull
    EntityName entityName;

    @NonNull
    EntityId entityId;

    @NonNull
    @With
    VersionConstraint versionConstraint;

    public static EntityRequest forEntity(EntityName entityName, EntityId entityId) {
        return new EntityRequest(entityName, entityId, Version.unspecified());
    }

    public String toString() {
        return "Entity '%s' %s (matching version %s)".formatted(entityName, entityId, versionConstraint);
    }

}
