package com.contentgrid.appserver.query.engine.jooq.strategy;

import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import org.jooq.DSLContext;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public interface JOOQRelationStrategy<R extends Relation> {

    boolean isLinked(DSLContext dslContext, R relation, EntityId sourceId, EntityId targetId);

    void delete(DSLContext dslContext, R relation, EntityId id);

    void deleteAll(DSLContext dslContext, R relation);
}
