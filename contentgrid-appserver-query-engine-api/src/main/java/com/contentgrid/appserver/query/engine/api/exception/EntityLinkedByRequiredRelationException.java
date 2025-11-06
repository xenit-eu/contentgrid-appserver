package com.contentgrid.appserver.query.engine.api.exception;

import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.domain.values.EntityId;
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
}
