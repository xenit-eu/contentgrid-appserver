package com.contentgrid.appserver.query.engine.jooq.strategy;

import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.domain.values.EntityId;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;

public sealed interface JOOQRelationStrategy<R extends Relation>
        permits HasSourceTableColumnRef, JOOQXToManyRelationStrategy, JOOQXToOneRelationStrategy {

    Table<?> getTable(R relation);

    Field<UUID> getSourceRef(R relation);

    Field<UUID> getTargetRef(R relation);

    void make(DSLContext dslContext, R relation);

    void destroy(DSLContext dslContext, R relation);

    boolean isLinked(DSLContext dslContext, R relation, EntityId sourceId, EntityId targetId);

    void delete(DSLContext dslContext, R relation, EntityId id);

    void deleteAll(DSLContext dslContext, R relation);
}
