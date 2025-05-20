package com.contentgrid.appserver.query.engine.jooq.strategy;

import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.query.engine.api.data.XToManyRelationData;
import com.contentgrid.appserver.query.engine.api.exception.EntityNotFoundException;
import com.contentgrid.appserver.query.engine.jooq.JOOQUtils;
import java.util.UUID;
import lombok.NonNull;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public abstract class JOOQXToManyRelationStrategy<R extends Relation> implements JOOQRelationStrategy<R> {

    protected abstract Table<?> getTable(R relation);

    protected abstract Field<UUID> getSourceRef(R relation);

    protected abstract Field<UUID> getTargetRef(R relation);

    protected void assertEntityExists(@NonNull DSLContext dslContext, @NonNull Entity entity, @NonNull EntityId id) {
        var table = JOOQUtils.resolveTable(entity);
        var primaryKey = JOOQUtils.resolvePrimaryKey(entity);
        if (!dslContext.fetchExists(table, primaryKey.eq(id.getValue()))) {
            throw new EntityNotFoundException("Entity '%s' with primary key '%s' does not exist.".formatted(entity.getName(), id));
        }
    }

    @Override
    public boolean isLinked(DSLContext dslContext, R relation, EntityId sourceId, EntityId targetId) {
        var table = getTable(relation);
        var sourceRef = getSourceRef(relation);
        var targetRef = getTargetRef(relation);

        return dslContext.fetchExists(DSL.selectOne().from(table)
                .where(DSL.and(sourceRef.eq(sourceId.getValue()), targetRef.eq(targetId.getValue()))));
    }

    public abstract void add(DSLContext dslContext, R relation, EntityId id, XToManyRelationData data);

    public abstract void remove(DSLContext dslContext, R relation, EntityId id, XToManyRelationData data);
}
