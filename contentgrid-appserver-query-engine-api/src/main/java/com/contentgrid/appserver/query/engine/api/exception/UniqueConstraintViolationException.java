package com.contentgrid.appserver.query.engine.api.exception;

import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import lombok.Getter;
import lombok.NonNull;

@Getter
public class UniqueConstraintViolationException extends ConstraintViolationException {

    /**
     * The existing entity that currently has the unique value which conflicted
     */
    @NonNull
    private final EntityIdentity conflictingEntity;

    public UniqueConstraintViolationException(
            EntityName entityName,
            EntityId entityId,
            PropertyPath propertyPath,
            EntityIdentity conflictingEntity
    ) {
        super(entityName, entityId, propertyPath);
        this.conflictingEntity = conflictingEntity;
    }
}
