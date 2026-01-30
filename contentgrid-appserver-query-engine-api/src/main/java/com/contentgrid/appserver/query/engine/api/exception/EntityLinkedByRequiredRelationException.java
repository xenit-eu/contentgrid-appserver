package com.contentgrid.appserver.query.engine.api.exception;

import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.values.EntityId;
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
     * The entity that is being deleted/unlinked
     */
    @NonNull
    @Getter
    private final EntityIdentity sourceIdentity;

    /**
     * The relation that holds the required side of the relation
     */
    @NonNull
    @Getter
    private final RelationIdentity targetRelationIdentity;

    /**
     *
     * @param relation The relation (where the target side is required)
     * @param sourceId The entity that is being deleted/unlinked
     * @param targetId The entity that holds the required side of the relation
     */
    public EntityLinkedByRequiredRelationException(@NonNull Relation relation, @NonNull EntityId sourceId, @NonNull EntityId targetId) {
        this(
                EntityIdentity.forEntity(relation.getSourceEndPoint().getEntity(), sourceId),
                RelationIdentity.forRelation(relation.getTargetEndPoint().getEntity(), targetId, relation.getTargetEndPoint()
                        .getName())
        );

    }

    @Override
    public String getMessage() {
        return "%s is required by %s".formatted(
                getSourceIdentity(),
                getTargetRelationIdentity()
        );
    }
}
