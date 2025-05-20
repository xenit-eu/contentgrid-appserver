package com.contentgrid.appserver.query.engine.jooq.strategy;

import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.query.engine.api.data.XToOneRelationData;
import com.contentgrid.appserver.query.engine.api.exception.ConstraintViolationException;
import com.contentgrid.appserver.query.engine.api.exception.EntityNotFoundException;
import com.contentgrid.appserver.query.engine.jooq.JOOQUtils;
import java.util.Optional;
import java.util.UUID;
import lombok.NonNull;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.exception.IntegrityConstraintViolationException;
import org.jooq.impl.DSL;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public abstract class JOOQXToOneRelationStrategy<R extends Relation> implements JOOQRelationStrategy<R> {

    protected abstract Table<?> getTable(R relation);

    protected abstract Field<UUID> getSourceRef(R relation);

    protected abstract Field<UUID> getTargetRef(R relation);

    protected abstract Field<UUID> getPrimaryKey(R relation);

    protected abstract Field<UUID> getForeignKey(R relation);

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

    public Optional<EntityId> findTarget(DSLContext dslContext, R relation, EntityId id) {
        var table = getTable(relation);
        var sourceRef = getSourceRef(relation);
        var targetRef = getTargetRef(relation);

        var result = dslContext.select(targetRef)
                .from(table)
                .where(sourceRef.eq(id.getValue()))
                .fetchOne();

        if (result == null) {
            // no rows found matching where clause
            assertEntityExists(dslContext, relation.getSourceEndPoint().getEntity(), id);
            return Optional.empty();
        } else if (result.value1() == null) {
            // row found, but target id was null
            return Optional.empty();
        } else {
            return Optional.of(EntityId.of(result.value1()));
        }
    }

    public abstract void create(DSLContext dslContext, R relation, EntityId id, XToOneRelationData data);

    @Override
    public void delete(DSLContext dslContext, R relation, EntityId id) {
        var table = getTable(relation);
        var sourceRef = getSourceRef(relation);
        var foreignKey = getForeignKey(relation);

        try {
            var updated = dslContext.update(table)
                    .set(foreignKey, (UUID) null)
                    .where(sourceRef.eq(id.getValue()))
                    .execute();

            if (updated == 0) {
                assertEntityExists(dslContext, relation.getSourceEndPoint().getEntity(), id);
            }
        } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
            throw new ConstraintViolationException(e.getMessage(), e); // this endpoint could be required
        }
    }

    @Override
    public void deleteAll(DSLContext dslContext, R relation) {
        var table = getTable(relation);
        var foreignKey = getForeignKey(relation);

        try {
            dslContext.update(table)
                    .set(foreignKey, (UUID) null)
                    .execute();
        } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
            throw new ConstraintViolationException(e.getMessage(), e); // this endpoint could be required
        }
    }
}
