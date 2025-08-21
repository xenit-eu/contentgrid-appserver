package com.contentgrid.appserver.query.engine.api.exception;

import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.domain.values.EntityId;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class RelationLinkNotFoundException extends QueryEngineException {
    @NonNull
    private final Relation relation;
    @NonNull
    private final EntityId sourceId;
    @NonNull
    private final EntityId targetId;

    public EntityName getSourceEntityName() {
        return relation.getSourceEndPoint().getEntity().getName();
    }

    public RelationName getSourceRelationName() {
        return relation.getSourceEndPoint().getName();
    }

    public EntityName getTargetEntityName() {
        return relation.getTargetEndPoint().getEntity().getName();
    }

    @Override
    public String getMessage() {
        return "Entity %s '%s' relation %s is not linked to %s '%s'".formatted(
                getSourceEntityName(),
                sourceId,
                getSourceRelationName(),
                getTargetEntityName(),
                targetId
        );
    }
}
