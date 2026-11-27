package com.contentgrid.appserver.domain.values.exception;

import com.contentgrid.appserver.domain.values.RelationIdentity;
import lombok.Getter;
import lombok.NonNull;

/**
 * Exception thrown when the target of a relation was not found in the database
 * <p>
 * This only occurs during linking a new item to a relation
 */
@Getter
public class RelationTargetNotFoundException extends RuntimeException {
    private final RelationIdentity relation;

    public RelationTargetNotFoundException(@NonNull RelationIdentity relation) {
        this.relation = relation;
    }

    @Override
    public String getMessage() {
        return "Can not link item:\nRelationIdentity:%s".formatted(relation);
    }
}
