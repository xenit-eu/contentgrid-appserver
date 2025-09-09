package com.contentgrid.appserver.query.engine.jooq.strategy;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.domain.values.EntityId;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;

public sealed interface JOOQRelationStrategy<R extends Relation>
        permits HasSourceTableColumnRef, JOOQXToManyRelationStrategy, JOOQXToOneRelationStrategy {

    Table<?> getTable(Application application, R relation);

    Field<UUID> getSourceRef(Application application, R relation);

    Field<UUID> getTargetRef(Application application, R relation);

    void make(DSLContext dslContext, Application application, R relation);

    void destroy(DSLContext dslContext, Application application, R relation);

    boolean isLinked(DSLContext dslContext, Application application, R relation, EntityId sourceId, EntityId targetId);

    void delete(DSLContext dslContext, Application application, R relation, EntityId id);

    void deleteAll(DSLContext dslContext, Application application, R relation);
}
