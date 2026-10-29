package com.contentgrid.appserver.domain.values.exception;

import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.domain.values.EntityRequest;
import lombok.Getter;
import lombok.NonNull;

/**
 * Exception thrown when no entity matching the given id was found in the database.
 */
@Getter
public class EntityIdNotFoundException extends RuntimeException { //TODO ACC-3004 change to checked exception
    @NonNull
    private final EntityName entityName;
    @NonNull
    private final EntityId id;

    public EntityIdNotFoundException(@NonNull EntityName entityName, @NonNull EntityId entityId) {
        super(formatMessage(entityName, entityId));
        this.entityName = entityName;
        this.id = entityId;
    }

    public EntityIdNotFoundException(@NonNull EntityName entityName, @NonNull EntityId entityId, @NonNull Throwable cause) {
        super(formatMessage(entityName, entityId), cause);
        this.entityName = entityName;
        this.id = entityId;
    }

    public EntityIdNotFoundException(EntityRequest request) {
        this(request.getEntityName(), request.getEntityId());
    }

    public EntityIdNotFoundException(EntityIdentity entityIdentity) {
        this(entityIdentity.getEntityName(), entityIdentity.getEntityId());
    }

    @Override
    public String getMessage() {
        return "No entity '%s' found with id '%s'".formatted(entityName, id);
    }

    public static String formatMessage(EntityName entityName, EntityId entityId) {
        return "No entity '%s' found with id '%s'".formatted(entityName, entityId);
    }
}
