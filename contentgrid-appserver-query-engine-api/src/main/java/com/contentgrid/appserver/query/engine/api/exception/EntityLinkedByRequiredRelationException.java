package com.contentgrid.appserver.query.engine.api.exception;

import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.domain.values.RelationIdentity;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Exception thrown when an entity is deleted or unlinked while it is still referenced by a required relation
 */
@RequiredArgsConstructor
public class EntityLinkedByRequiredRelationException extends QueryEngineException {
    /**
     * The relation (where the target side is required)
     */
    @NonNull
    private final Relation relation;

    /**
     * The entity that is being deleted/unlinked
     */
    @NonNull
    private final EntityId sourceId;

    /**
     * The entity that holds the required side of the relation
     */
    @NonNull
    private final EntityId targetId;

    public EntityIdentity getSourceIdentity() {
        return EntityIdentity.forEntity(relation.getSourceEndPoint().getEntity(), sourceId);
    }

    public EntityIdentity getTargetIdentity() {
        return EntityIdentity.forEntity(relation.getTargetEndPoint().getEntity(), targetId);
    }

    public RelationIdentity getSourceRelationIdentity() {
        return RelationIdentity.forRelation(getSourceIdentity(), relation.getSourceEndPoint().getName());
    }

    public RelationIdentity getTargetRelationIdentity() {
        return RelationIdentity.forRelation(getTargetIdentity(), relation.getTargetEndPoint().getName());
    }

    @Override
    public String getMessage() {
        return "%s is required by %s".formatted(
                getSourceIdentity(),
                getTargetRelationIdentity()
        );
    }
}
