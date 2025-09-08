package com.contentgrid.appserver.query.engine.jooq.strategy;

import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.domain.values.EntityId;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public abstract sealed class JOOQRelationStrategy<R extends Relation>
        permits JOOQXToOneRelationStrategy, JOOQXToManyRelationStrategy {

    public abstract Table<?> getTable(R relation);

    public abstract Field<UUID> getSourceRef(R relation);

    public abstract Field<UUID> getTargetRef(R relation);

    public abstract void make(DSLContext dslContext, R relation);

    public abstract void destroy(DSLContext dslContext, R relation);

    public abstract boolean isLinked(DSLContext dslContext, R relation, EntityId sourceId, EntityId targetId);

    public abstract void delete(DSLContext dslContext, R relation, EntityId id);

    public abstract void deleteAll(DSLContext dslContext, R relation);
}
