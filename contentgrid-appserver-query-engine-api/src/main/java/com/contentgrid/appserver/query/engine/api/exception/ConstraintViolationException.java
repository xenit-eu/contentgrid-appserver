package com.contentgrid.appserver.query.engine.api.exception;

import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import com.contentgrid.appserver.domain.values.EntityId;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public abstract class ConstraintViolationException extends QueryEngineException {

    @NonNull
    private final EntityName entityName;

    @NonNull
    private final EntityId entityId;

    @NonNull
    private final PropertyPath propertyPath;

}
