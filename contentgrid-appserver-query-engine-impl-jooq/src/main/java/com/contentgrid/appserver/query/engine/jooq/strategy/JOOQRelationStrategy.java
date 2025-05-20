package com.contentgrid.appserver.query.engine.jooq.strategy;

import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.query.engine.api.exception.EntityNotFoundException;
import com.contentgrid.appserver.query.engine.jooq.JOOQUtils;
import java.util.UUID;
import lombok.NonNull;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public abstract class JOOQRelationStrategy<R extends Relation> {

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

    public abstract boolean isLinked(DSLContext dslContext, R relation, EntityId sourceId, EntityId targetId);

    public abstract void delete(DSLContext dslContext, R relation, EntityId id);

    public abstract void deleteAll(DSLContext dslContext, R relation);
}
