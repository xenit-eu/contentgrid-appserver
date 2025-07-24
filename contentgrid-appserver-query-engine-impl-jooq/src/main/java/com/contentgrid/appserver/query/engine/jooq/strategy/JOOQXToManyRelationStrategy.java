package com.contentgrid.appserver.query.engine.jooq.strategy;

import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import java.util.Set;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public abstract sealed class JOOQXToManyRelationStrategy<R extends Relation> extends JOOQRelationStrategy<R>
        permits JOOQOneToManyRelationStrategy, JOOQManyToManyRelationStrategy {

    @Override
    public boolean isLinked(DSLContext dslContext, R relation, EntityId sourceId, EntityId targetId) {
        var table = getTable(relation);
        var sourceRef = getSourceRef(relation);
        var targetRef = getTargetRef(relation);

        return dslContext.fetchExists(DSL.selectOne().from(table)
                .where(DSL.and(sourceRef.eq(sourceId.getValue()), targetRef.eq(targetId.getValue()))));
    }

    public abstract void add(DSLContext dslContext, R relation, EntityId id, Set<EntityId> data);

    public abstract void remove(DSLContext dslContext, R relation, EntityId id, Set<EntityId> data);
}
