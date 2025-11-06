package com.contentgrid.appserver.query.engine.api.exception;

import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import com.contentgrid.appserver.domain.values.EntityId;
import lombok.NonNull;

public class RequiredConstraintViolationException extends ConstraintViolationException {

    public RequiredConstraintViolationException(
            @NonNull EntityName entityName,
            @NonNull EntityId entityId,
            @NonNull PropertyPath propertyPath) {
        super(entityName, entityId, propertyPath);
    }
}
