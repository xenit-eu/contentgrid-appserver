package com.contentgrid.appserver.query.engine.api.exception;

import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.domain.values.RelationIdentity;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Exception thrown when a relation operation would result in a blind overwrite.
 * <p>
 * A blind overwrite occurs when a relation write operation cannot be protected by optimistic locking
 * and would result in silently overwriting an existing relation on the inverse side without proper
 * concurrency control. This can happen in one-to-many or one-to-one relation scenarios where
 * the target entity already has an established relationship.
 * <p>
 * This exception serves as a safety mechanism to prevent data loss and maintain data integrity
 * by detecting and preventing potentially destructive relation updates that could occur without
 * the user's knowledge.
 */
@RequiredArgsConstructor
@Getter
public class BlindRelationOverwriteException extends QueryEngineException {

    /**
     * The identity of the relation that would be blindly overwritten.
     */
    @NonNull
    private final RelationIdentity affectedRelation;

    /**
     * The identity of the entity that is currently referenced by the relation, and which would have been overwritten.
     */
    @NonNull
    private final EntityIdentity originalValue;

    @Override
    public String getMessage() {
        return "Operation would blindly overwrite %s referencing %s".formatted(affectedRelation, originalValue);
    }
}
