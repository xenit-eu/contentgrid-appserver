package com.contentgrid.appserver.query.engine.jooq.strategy;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.domain.values.RelationIdentity;
import com.contentgrid.appserver.query.engine.api.exception.BlindRelationOverwriteException;
import com.contentgrid.appserver.query.engine.api.exception.ConstraintViolationException;
import com.contentgrid.appserver.query.engine.api.exception.EntityIdNotFoundException;
import com.contentgrid.appserver.query.engine.api.exception.InvalidSqlException;
import com.contentgrid.appserver.query.engine.jooq.JOOQUtils;
import com.contentgrid.appserver.query.engine.jooq.strategy.ExpectedId.IdSpecified;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.SneakyThrows;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.exception.IntegrityConstraintViolationException;
import org.jooq.impl.DSL;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.BadSqlGrammarException;

public abstract sealed class JOOQXToOneRelationStrategy<R extends Relation> implements JOOQRelationStrategy<R>
        permits JOOQSourceOneToOneRelationStrategy, JOOQManyToOneRelationStrategy, JOOQTargetOneToOneRelationStrategy {

    protected abstract Field<UUID> getPrimaryKey(Application application, R relation);

    protected abstract Field<UUID> getForeignKey(Application application, R relation);

    protected abstract Entity getForeignEntity(Application application, R relation);

    @Override
    public void make(DSLContext dslContext, Application application, R relation) {
        var table = getTable(application, relation);
        var foreignKey = getForeignKey(application, relation);
        var foreignEntity = getForeignEntity(application, relation);
        var foreignTable = JOOQUtils.resolveTable(foreignEntity);
        var foreignPrimaryKey = JOOQUtils.resolvePrimaryKey(foreignEntity);

        try {
            dslContext.alterTable(table)
                    .add(foreignKey, DSL.foreignKey(foreignKey).references(foreignTable, foreignPrimaryKey))
                    .execute();
        } catch (BadSqlGrammarException e) {
            throw new InvalidSqlException(e.getMessage(), e);
        }
    }

    @Override
    public void destroy(DSLContext dslContext, Application application, R relation) {
        var table = getTable(application, relation);
        var foreignKey = getForeignKey(application, relation);
        try {
            dslContext.alterTable(table).dropColumnIfExists(foreignKey).execute();
        } catch (BadSqlGrammarException e) {
            throw new InvalidSqlException(e.getMessage(), e); // table could not exist
        }
    }

    @Override
    public boolean isLinked(DSLContext dslContext, Application application, R relation, EntityId sourceId, EntityId targetId) {
        var table = getTable(application, relation);
        var sourceRef = getSourceRef(application, relation);
        var targetRef = getTargetRef(application, relation);

        return dslContext.fetchExists(DSL.selectOne().from(table)
                .where(DSL.and(sourceRef.eq(sourceId.getValue()), targetRef.eq(targetId.getValue()))));
    }

    public Optional<EntityId> findTarget(DSLContext dslContext, Application application, R relation, EntityId id) {
        var table = getTable(application, relation);
        var sourceRef = getSourceRef(application, relation);
        var targetRef = getTargetRef(application, relation);

        return dslContext.select(targetRef)
                .from(table)
                .where(sourceRef.eq(id.getValue()))
                .fetchOptional()
                .map(Record1::value1)
                .map(EntityId::of);
    }

    public void create(DSLContext dslContext, Application application, R relation, EntityId id, EntityId targetId,
            ExpectedId expectedId)
            throws ExpectedIdMismatchException {
        setValue(dslContext, application, relation, id, expectedId, targetId.getValue());
    }

    @Override
    // This exception actually doesn't happen here, because we don't expect any ID
    @SneakyThrows(ExpectedIdMismatchException.class)
    public void delete(DSLContext dslContext, Application application, R relation, EntityId id) {
        delete(dslContext, application, relation, id, ExpectedId.unspecified());
    }

    public void delete(DSLContext dslContext, Application application, R relation, EntityId id, ExpectedId expectedId)
            throws ExpectedIdMismatchException {
        setValue(dslContext, application, relation, id, expectedId, null);
    }

    private void setValue(DSLContext dslContext, Application application, R relation, EntityId id, ExpectedId expectedId, UUID targetValue) throws ExpectedIdMismatchException {
        var table = getTable(application, relation);
        var sourceRef = getSourceRef(application, relation);
        var targetRef = getTargetRef(application, relation);

        var savepointName = DSL.name("x_to_one_update_"+UUID.randomUUID());
        dslContext.savepoint(savepointName).execute();

        try {
            var newValue = dslContext.update(table)
                    .set(targetRef, expectedId.mapToNewValue(targetRef, targetRef, targetValue))
                    .where(sourceRef.eq(id.getValue()))
                    .returning(targetRef)
                    .fetchOptional()
                    .orElseThrow(() -> {
                        var entityName = relation.getSourceEndPoint().getEntity();
                        return new EntityIdNotFoundException(entityName, id);
                    })
                    .get(targetRef);

            if (!Objects.equals(newValue, targetValue)) {
                throw new ExpectedIdMismatchException((IdSpecified) expectedId, newValue);
            }
        } catch (DuplicateKeyException e) {
            dslContext.rollback().toSavepoint(savepointName).execute();

            var conflictingRowId = dslContext.select(sourceRef)
                    .from(table)
                    .where(targetRef.eq(targetValue))
                    .fetchOptional(sourceRef);

            var ex = new BlindRelationOverwriteException(
                    RelationIdentity.forRelation(
                            relation.getTargetEndPoint().getEntity(),
                            EntityId.of(targetValue),
                            relation.getTargetEndPoint().getName()
                    ),
                    EntityIdentity.forEntity(
                            relation.getSourceEndPoint().getEntity(),
                            EntityId.of(conflictingRowId.orElseThrow())
                    )
            );
            ex.initCause(e);
            throw ex;
        } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
            throw new ConstraintViolationException(e.getMessage(), e); // also thrown when foreign key was not found
        }
    }

    @Override
    public void deleteAll(DSLContext dslContext, Application application, R relation) {
        var table = getTable(application, relation);
        var foreignKey = getForeignKey(application, relation);

        try {
            dslContext.update(table)
                    .set(foreignKey, (UUID) null)
                    .execute();
        } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
            throw new ConstraintViolationException(e.getMessage(), e); // this endpoint could be required
        }
    }
}
