package com.contentgrid.appserver.rest.exception;

import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.query.engine.api.exception.EntityIdNotFoundException;
import java.util.Arrays;
import lombok.Getter;
import lombok.NonNull;

@Getter
public class RelationTargetNotFoundException extends Exception {
    @NonNull
    private final EntityName targetEntityName;
    @NonNull
    private final EntityId targetEntityId;

    public RelationTargetNotFoundException(EntityIdNotFoundException cause) {
        super("Invalid relation target: %s '%s'".formatted(cause.getEntityName().getValue(), cause.getId().getValue()), cause);
        this.targetEntityName = cause.getEntityName();
        this.targetEntityId = cause.getId();
        Arrays.stream(cause.getSuppressed())
                .filter(EntityIdNotFoundException.class::isInstance)
                .map(EntityIdNotFoundException.class::cast)
                .map(RelationTargetNotFoundException::new)
                .forEach(this::addSuppressed);
    }

}
