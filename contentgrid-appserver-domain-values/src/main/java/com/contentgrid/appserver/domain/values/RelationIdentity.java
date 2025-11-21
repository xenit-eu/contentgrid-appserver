package com.contentgrid.appserver.domain.values;

import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.domain.values.version.UnspecifiedVersion;
import com.contentgrid.appserver.domain.values.version.Version;
import java.io.Serializable;
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
public class RelationIdentity implements Serializable {
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

    public static RelationIdentity forRelation(@NonNull EntityIdentity entityIdentity, @NonNull RelationName relationName) {
        return forRelation(entityIdentity.getEntityName(), entityIdentity.getEntityId(), relationName);
    }

    private EntityIdentity getEntityIdentity() {
        return EntityIdentity.forEntity(entityName, entityId);
    }

    public RelationRequest toRequest() {
        return RelationRequest.forRelation(entityName, entityId, relationName).withVersionConstraint(version);
    }

    public String toString() {
        if(version instanceof UnspecifiedVersion) {
            return "Relation '%s' on %s".formatted(relationName, getEntityIdentity());
        }
        return "Relation '%s' on %s (%s)".formatted(relationName, getEntityIdentity(), version);
    }

}
