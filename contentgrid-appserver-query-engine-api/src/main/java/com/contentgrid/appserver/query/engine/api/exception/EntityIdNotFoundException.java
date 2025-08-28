package com.contentgrid.appserver.query.engine.api.exception;

import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityRequest;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Exception thrown when no entity matching the given id was found in the database.
 */
@Getter
@RequiredArgsConstructor
public class EntityIdNotFoundException extends QueryEngineException {
    @NonNull
    private final EntityName entityName;
    @NonNull
    private final EntityId id;

    public EntityIdNotFoundException(EntityRequest request) {
        this(request.getEntityName(), request.getEntityId());
    }

    @Override
    public String getMessage() {
        return "No entity '%s' found with id '%s'".formatted(entityName, id);
    }
}
