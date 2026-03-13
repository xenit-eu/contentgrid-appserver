package com.contentgrid.appserver.query.engine.api.exception;

import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.domain.values.RelationIdentity;
import lombok.Getter;
import lombok.NonNull;

/**
 * Exception thrown when the target of a relation was not found in the database
 * <p>
 * This only occurs during linking a new item to a relation
 */
@Getter
public class RelationTargetNotFoundException extends EntityIdNotFoundException {
    private final RelationIdentity relation;

    public RelationTargetNotFoundException(@NonNull EntityIdentity entity, @NonNull RelationIdentity relation) {
        super(entity.getEntityName(), entity.getEntityId());
        this.relation = relation;
    }

    @Override
    public String getMessage() {
        return "Can not link item to %s: %s".formatted(relation, super.getMessage());
    }
}
