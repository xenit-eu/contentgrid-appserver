package com.contentgrid.appserver.query.engine.api.exception;

import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class PermissionDeniedException extends QueryEngineException {

    @NonNull
    private final EntityName entityName;
    @NonNull
    private final EntityId entityId;

    public PermissionDeniedException(EntityIdentity entityIdentity) {
        this(entityIdentity.getEntityName(), entityIdentity.getEntityId());
    }

    @Override
    public String getMessage() {
        return "Permission denied on entity '%s' with id '%s'".formatted(entityName, entityId);
    }
}
