package com.contentgrid.appserver.query.engine.api.exception;

import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.domain.values.RelationIdentity;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class RelationLinkNotFoundException extends QueryEngineException {

    @NonNull
    private final RelationIdentity sourceRelationIdentity;
    @NonNull
    private final EntityIdentity targetEntity;

    public RelationLinkNotFoundException(@NonNull Relation relation, @NonNull EntityId sourceId, @NonNull EntityId targetId) {
        this(
                RelationIdentity.forRelation(
                        relation.getSourceEndPoint().getEntity(),
                        sourceId,
                        relation.getSourceEndPoint().getName()
                ),
                EntityIdentity.forEntity(
                        relation.getTargetEndPoint().getEntity(),
                        targetId
                )
        );

    }

    @Override
    public String getMessage() {
        return "%s is not linked to %s".formatted(sourceRelationIdentity, targetEntity);
    }
}
