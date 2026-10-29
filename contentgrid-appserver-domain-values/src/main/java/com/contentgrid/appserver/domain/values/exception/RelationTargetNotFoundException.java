package com.contentgrid.appserver.domain.values.exception;

import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.domain.values.EntityId;
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

    public RelationTargetNotFoundException(
            @NonNull EntityName entityName,
            @NonNull EntityId entityId,
            @NonNull RelationIdentity relation) {
        super(entityName, entityId);
        this.relation = relation;
    }

    public RelationTargetNotFoundException(
            @NonNull EntityName entityName,
            @NonNull EntityId entityId,
            @NonNull RelationIdentity relation,
            @NonNull Throwable cause) {
        super(entityName, entityId, cause);
        this.relation = relation;
    }

    public RelationTargetNotFoundException(
            @NonNull EntityIdentity entity,
            @NonNull RelationIdentity relation) {
        this(entity.getEntityName(), entity.getEntityId(), relation);
    }

    public RelationTargetNotFoundException(
            @NonNull EntityIdentity entityIdentity,
            @NonNull RelationIdentity relationIdentity,
            @NonNull Throwable cause
    ) {
        this(entityIdentity.getEntityName(), entityIdentity.getEntityId(), relationIdentity, cause);
    }

    @Override
    public String getMessage() {
        return "Can not link item to %s: %s".formatted(relation, super.getMessage());
    }
}
