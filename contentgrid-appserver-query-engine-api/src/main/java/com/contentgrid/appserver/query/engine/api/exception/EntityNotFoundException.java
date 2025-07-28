package com.contentgrid.appserver.query.engine.api.exception;

import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class EntityNotFoundException extends QueryEngineException {
    @NonNull
    private final EntityName entityName;
    @NonNull
    private final EntityId id;

    @Override
    public String getMessage() {
        return "No entity '%s' found with id '%s'".formatted(entityName, id);
    }
}
