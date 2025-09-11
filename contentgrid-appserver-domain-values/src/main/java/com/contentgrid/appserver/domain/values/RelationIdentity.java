package com.contentgrid.appserver.domain.values;

import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.domain.values.version.Version;
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
public class RelationIdentity {
    @NonNull
    EntityName entityName;

    @NonNull
    EntityId entityId;

    @NonNull
    RelationName relationName;

    @NonNull
    @With
    Version version;

    public static RelationIdentity forRelation(EntityName entityName, EntityId entityId, RelationName relationName) {
        return new RelationIdentity(entityName, entityId, relationName, Version.unspecified());
    }

    public RelationRequest toRequest() {
        return RelationRequest.forRelation(entityName, entityId, relationName).withVersionConstraint(version);
    }

    public String toString() {
        return "Relation '%s' on '%s' %s (%s)".formatted(relationName, entityName, entityId, version);
    }

}
