package com.contentgrid.appserver.domain.values;

import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.domain.values.version.Version;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.With;

/**
 * Unique identity of a specific entity (optionally pinned to a specific version)
 */
@Value
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class EntityIdentity {

    @NonNull
    EntityName entityName;

    @NonNull
    EntityId entityId;

    @NonNull
    @With
    Version version;

    public static EntityIdentity forEntity(EntityName entityName, EntityId entityId) {
        return new EntityIdentity(entityName, entityId, Version.unspecified());
    }

    public String toString() {
        return "Entity '%s' %s (%s)".formatted(entityName, entityId, version);
    }
}
