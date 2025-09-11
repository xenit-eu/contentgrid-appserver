package com.contentgrid.appserver.domain.values;

import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.domain.values.version.Version;
import com.contentgrid.appserver.domain.values.version.VersionConstraint;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.With;

/**
 * Unique identity of a specific relation (optionally pinned to a specific version)
 */
@Value
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class RelationRequest {
    @NonNull
    EntityName entityName;

    @NonNull
    EntityId entityId;

    @NonNull
    RelationName relationName;

    @NonNull
    @With
    VersionConstraint versionConstraint;

    public static RelationRequest forRelation(EntityName entityName, EntityId entityId, RelationName relationName) {
        return new RelationRequest(entityName, entityId, relationName, VersionConstraint.ANY);
    }

    public String toString() {
        return "Relation '%s' on '%s' %s (matching version %s)".formatted(relationName, entityName, entityId,
                versionConstraint);
    }

}
