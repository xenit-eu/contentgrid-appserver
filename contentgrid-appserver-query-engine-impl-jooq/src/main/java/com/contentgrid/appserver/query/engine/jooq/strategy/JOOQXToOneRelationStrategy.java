package com.contentgrid.appserver.query.engine.jooq.strategy;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.values.RelationPath;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.domain.values.RelationIdentity;
import com.contentgrid.appserver.query.engine.api.exception.BlindRelationOverwriteException;
import com.contentgrid.appserver.query.engine.api.exception.EntityIdNotFoundException;
import com.contentgrid.appserver.query.engine.api.exception.RelationTargetNotFoundException;
import com.contentgrid.appserver.query.engine.api.exception.RequiredConstraintViolationException;
import com.contentgrid.appserver.query.engine.jooq.DslContextUtils;
import com.contentgrid.appserver.query.engine.jooq.ExceptionUtils;
import com.contentgrid.appserver.query.engine.jooq.JOOQUtils;
import com.contentgrid.appserver.query.engine.jooq.PostgresqlErrorType;
import com.contentgrid.appserver.query.engine.jooq.strategy.ExpectedId.IdSpecified;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.SneakyThrows;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

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

        dslContext.alterTable(table)
                .addIfNotExists(foreignKey)
                .execute();
        dslContext.alterTable(table)
                .add(DSL.foreignKey(foreignKey).references(foreignTable, foreignPrimaryKey))
                .execute();
    }

    @Override
    public void destroy(DSLContext dslContext, Application application, R relation) {
        var table = getTable(application, relation);
        var foreignKey = getForeignKey(application, relation);
        dslContext.alterTable(table).dropColumnIfExists(foreignKey).execute();
    }

    @Override
    public boolean isLinked(DSLContext dslContext, Application application, R relation, EntityIdentity sourceIdentity, EntityIdentity targetIdentity) {
        var table = getTable(application, relation);
        var sourceRef = getSourceRef(application, relation);
        var targetRef = getTargetRef(application, relation);

        return dslContext.fetchExists(DSL.selectOne().from(table)
                .where(DSL.and(sourceRef.eq(sourceIdentity.getEntityId().getValue()), targetRef.eq(targetIdentity.getEntityId().getValue()))));
    }

    public Optional<EntityId> findTarget(DSLContext dslContext, Application application, R relation, EntityIdentity sourceIdentity) {
        var table = getTable(application, relation);
        var sourceRef = getSourceRef(application, relation);
        var targetRef = getTargetRef(application, relation);

        return dslContext.select(targetRef)
                .from(table)
                .where(sourceRef.eq(sourceIdentity.getEntityId().getValue()))
                .fetchOptional()
                .map(Record1::value1)
                .map(EntityId::of);
    }

    public void create(DSLContext dslContext, Application application, R relation, EntityIdentity sourceIdentity, EntityIdentity targetIdentity,
            ExpectedId expectedTargetId)
            throws ExpectedIdMismatchException {
        setValue(dslContext, application, relation, sourceIdentity, expectedTargetId, targetIdentity.getEntityId().getValue());
    }

    @Override
    // This exception actually doesn't happen here, because we don't expect any ID
    @SneakyThrows(ExpectedIdMismatchException.class)
    public void delete(DSLContext dslContext, Application application, R relation, EntityIdentity sourceIdentity) {
        delete(dslContext, application, relation, sourceIdentity, ExpectedId.unspecified());
    }

    public void delete(DSLContext dslContext, Application application, R relation, EntityIdentity sourceIdentity, ExpectedId expectedTargetId)
            throws ExpectedIdMismatchException {
        setValue(dslContext, application, relation, sourceIdentity, expectedTargetId, null);
    }

    private void setValue(DSLContext dslContext, Application application, R relation, EntityIdentity sourceIdentity, ExpectedId expectedTargetId, UUID targetValue) throws ExpectedIdMismatchException {
        var table = getTable(application, relation);
        var sourceRef = getSourceRef(application, relation);
        var targetRef = getTargetRef(application, relation);


        try {
            var newValue = DslContextUtils.executeInSavepoint(dslContext, () -> dslContext.update(table)
                    .set(targetRef, expectedTargetId.mapToNewValue(targetRef, targetRef, targetValue))
                    .where(sourceRef.eq(sourceIdentity.getEntityId().getValue()))
                    .returning(targetRef)
                    .fetchOptional()
                    .orElseThrow(() -> new EntityIdNotFoundException(sourceIdentity))
                    .get(targetRef));

            if (!Objects.equals(newValue, targetValue)) {
                throw new ExpectedIdMismatchException((IdSpecified) expectedTargetId, newValue);
            }
        } catch (DataAccessException e) {
            throw switch (PostgresqlErrorType.from(e)) {
                case UNIQUE_CONSTRAINT_VIOLATION -> ExceptionUtils.handleException(e, () -> {
                    var conflictingRowId = dslContext.select(sourceRef)
                            .from(table)
                            .where(targetRef.eq(targetValue))
                            .fetchOptional(sourceRef);

                    return new BlindRelationOverwriteException(
                            RelationIdentity.forRelation(sourceIdentity, relation.getSourceEndPoint().getName()),
                            EntityIdentity.forEntity(
                                    relation.getTargetEndPoint().getEntity(),
                                    EntityId.of(targetValue)
                            ),
                            EntityIdentity.forEntity(
                                    relation.getSourceEndPoint().getEntity(),
                                    EntityId.of(conflictingRowId.orElseThrow())
                            )
                    );
                });
                case FOREIGN_KEY_CONSTRAINT_VIOLATION -> ExceptionUtils.handleException(e , () -> {
                    if(targetValue != null) {
                        // A foreign-key constraint violation can only happen when *setting* a new value
                        // (because the target id that is being set does not actually exist
                        return new RelationTargetNotFoundException(
                                EntityIdentity.forEntity(relation.getTargetEndPoint().getEntity(), EntityId.of(targetValue)),
                                RelationIdentity.forRelation(sourceIdentity, relation.getSourceEndPoint().getName())
                        );
                    }
                    return null;
                });
                case NOT_NULL_CONSTRAINT_VIOLATION -> ExceptionUtils.handleException(e, () -> {
                    // A not null constraint violation can only happen when clearing a value
                    // (because otherwise, we would not be setting a null value)
                    return new RequiredConstraintViolationException(
                            sourceIdentity.getEntityName(),
                            sourceIdentity.getEntityId(),
                            new RelationPath(relation.getSourceEndPoint().getName(), null)
                    );
                });
                default -> e;
            };
        }
    }

    @Override
    public void deleteAll(DSLContext dslContext, Application application, R relation) {
        var table = getTable(application, relation);
        var foreignKey = getForeignKey(application, relation);

        dslContext.update(table)
                .set(foreignKey, (UUID) null)
                .execute();
    }
}
