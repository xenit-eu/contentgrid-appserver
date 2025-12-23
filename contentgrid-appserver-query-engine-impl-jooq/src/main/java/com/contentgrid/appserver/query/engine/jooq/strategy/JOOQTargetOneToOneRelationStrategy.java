package com.contentgrid.appserver.query.engine.jooq.strategy;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.relations.TargetOneToOneRelation;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.domain.values.RelationIdentity;
import com.contentgrid.appserver.query.engine.api.exception.BlindRelationOverwriteException;
import com.contentgrid.appserver.query.engine.api.exception.ConcurrencyFailureException;
import com.contentgrid.appserver.query.engine.api.exception.EntityIdNotFoundException;
import com.contentgrid.appserver.query.engine.api.exception.EntityLinkedByRequiredRelationException;
import com.contentgrid.appserver.query.engine.jooq.DslContextUtils;
import com.contentgrid.appserver.query.engine.jooq.ExceptionUtils;
import com.contentgrid.appserver.query.engine.jooq.JOOQUtils;
import com.contentgrid.appserver.query.engine.jooq.PostgresqlErrorType;
import com.contentgrid.appserver.query.engine.jooq.strategy.ExpectedId.IdSpecified;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

final class JOOQTargetOneToOneRelationStrategy extends JOOQXToOneRelationStrategy<TargetOneToOneRelation> {

    @Override
    public Table<?> getTable(Application application, TargetOneToOneRelation relation) {
        return JOOQUtils.resolveTable(application.getRelationTargetEntity(relation));
    }

    @Override
    public Field<UUID> getSourceRef(Application application, TargetOneToOneRelation relation) {
        return getForeignKey(application, relation);
    }

    @Override
    public Field<UUID> getTargetRef(Application application, TargetOneToOneRelation relation) {
        return getPrimaryKey(application, relation);
    }

    @Override
    protected Field<UUID> getPrimaryKey(Application application, TargetOneToOneRelation relation) {
        return JOOQUtils.resolvePrimaryKey(application.getRelationTargetEntity(relation));
    }

    @Override
    protected Field<UUID> getForeignKey(Application application, TargetOneToOneRelation relation) {
        return (Field<UUID>) JOOQUtils.resolveField(relation.getSourceReference(), application.getRelationSourceEntity(relation).getPrimaryKey()
                .getType(), relation.getTargetEndPoint().isRequired());
    }

    @Override
    protected Entity getForeignEntity(Application application, TargetOneToOneRelation relation) {
        return application.getRelationSourceEntity(relation);
    }

    @Override
    public void make(DSLContext dslContext, Application application, TargetOneToOneRelation relation) {
        super.make(dslContext, application, relation);

        // Make column unique
        dslContext.alterTable(getTable(application, relation))
                .add(DSL.unique(getForeignKey(application, relation)))
                .execute();
    }

    @Override
    public void create(DSLContext dslContext, Application application, TargetOneToOneRelation relation, EntityId id,
            EntityId targetId, ExpectedId expectedTargetId) throws ExpectedIdMismatchException {
        var table = getTable(application, relation);
        var sourceRef = getSourceRef(application, relation);
        var targetRef = getTargetRef(application, relation);

        var newRowCondition = targetRef.eq(targetId.getValue());
        var oldRowCondition = sourceRef.eq(id.getValue());

        var newRowField = DSL.field(newRowCondition).as("_new_row_"+UUID.randomUUID());
        var oldRowField = DSL.field(oldRowCondition).as("_old_row_"+UUID.randomUUID());

        var rowsToUpdate = dslContext.select(
                        sourceRef,
                        targetRef,
                        newRowField,
                        oldRowField
                )
                .from(table)
                .where(newRowCondition.or(oldRowCondition))
                .forUpdate() // Needs to be for update (not for no key update), because updating a unique index is a key update
                .fetch();

        org.jooq.Record oldRecord = null;
        org.jooq.Record newRecord = null;

        for (var r : rowsToUpdate) {
            // When sourceRef is null, the expression sourceRef = ? is also null,
            // resulting in oldRowField also being null. In this case, null can be interpreted as false
            if(Boolean.TRUE.equals(r.get(oldRowField))) {
                oldRecord = r;
            }

            if(Boolean.TRUE.equals(r.get(newRowField))) {
                newRecord = r;
            }
        }

        var maybeOldId = Optional.ofNullable(oldRecord)
                .map(r -> r.get(targetRef));

        if(expectedTargetId instanceof IdSpecified expectedTargetIdSpecified) {
            if(!Objects.equals(expectedTargetIdSpecified.getEntityId().map(EntityId::getValue), maybeOldId)) {
                throw new ExpectedIdMismatchException(expectedTargetIdSpecified, maybeOldId.orElse(null));
            }
        }

        // A new record must be present, otherwise we are trying to write to an entity that does not exist
        if(newRecord == null) {
            throw new EntityIdNotFoundException(
                    relation.getTargetEndPoint().getEntity(),
                    targetId
            );
        } else if(newRecord.get(sourceRef) != null) {
            throw new BlindRelationOverwriteException(
                    RelationIdentity.forRelation(
                            relation.getSourceEndPoint().getEntity(),
                            id,
                            relation.getSourceEndPoint().getName()
                    ),
                    EntityIdentity.forEntity(
                            relation.getTargetEndPoint().getEntity(),
                            EntityId.of(newRecord.get(targetRef))
                    ),
                    EntityIdentity.forEntity(
                            relation.getSourceEndPoint().getEntity(),
                            EntityId.of(newRecord.get(sourceRef))
                    )
            );
        }

        try {

            // When there is an old record, we need to clear it out *before* setting the new record,
            // otherwise the unique constraint may fail, dependent on which order the rows are in
            if(oldRecord != null) {
                dslContext.update(table)
                        .set(sourceRef, (UUID) null)
                        .where(targetRef.eq(oldRecord.get(targetRef)))
                        .execute();
            }

            // If the same update is concurrently executed right here, we'll get a unique constraint violation
            // because the other execution has not seen 2 rows, and thus has not executed the update above

            dslContext.update(table)
                    .set(sourceRef, id.getValue())
                    .where(newRowCondition)
                    .execute();

        } catch (DataAccessException e) {
            // Unique constraint violation can not happen, because the query above ensures that the unique is wiped out first
            // Not null violation can not happen, because when he sourceRef is NOT NULL, a BlindRelationOverwriteException would have been thrown
            // (because newRecord.get(sourceRef) also would not be null
            switch (PostgresqlErrorType.from(e)) {
                case FOREIGN_KEY_CONSTRAINT_VIOLATION -> throw ExceptionUtils.handleException(e,
                        () -> new EntityIdNotFoundException(relation.getSourceEndPoint().getEntity(), id));
                // Unique constraint violation may happen under concurrent execution.
                // This is not directly recoverable. It can be recovered by retrying the whole transaction
                case UNIQUE_CONSTRAINT_VIOLATION -> throw new ConcurrencyFailureException(e);
                // Other unhandled exceptions are rethrown
                default -> throw e;
            }
        }
    }

    @Override
    public void delete(DSLContext dslContext, Application application, TargetOneToOneRelation relation, EntityId id,
            ExpectedId expectedTargetId) throws ExpectedIdMismatchException {
        var table = getTable(application, relation);
        var sourceRef = getSourceRef(application, relation);
        var targetRef = getTargetRef(application, relation);

        try {
            var maybeResult = DslContextUtils.executeInSavepoint(dslContext, () -> dslContext.update(table)
                    .set(sourceRef, expectedTargetId.mapToNewValue(targetRef, sourceRef, null))
                    .where(sourceRef.eq(id.getValue()))
                    .returning(sourceRef, targetRef)
                    .fetchOptional());

            if (maybeResult.isEmpty()) {
                // Nothing links to the entity.
                // This is fine for a delete, as the database is already in the expected state.
                // However, we still need to check that this matches the expected state, to properly signal failure when
                // it was expected that something was pointing to the entity
                if (expectedTargetId instanceof ExpectedId.ExactlyExpectedId exactlyExpectedId) {
                    throw new ExpectedIdMismatchException(exactlyExpectedId, null);
                }
                return;
            }

            var result = maybeResult.get();

            if (result.get(sourceRef) != null) {
                // Optimistic locking failure, sourceRef was not cleared, because it was not pointed to by the original target
                throw new ExpectedIdMismatchException((IdSpecified) expectedTargetId, result.get(targetRef));
            }
        } catch(DataAccessException e) {
            if(PostgresqlErrorType.from(e).is(PostgresqlErrorType.NOT_NULL_CONSTRAINT_VIOLATION)) {
                // Can happen when deleting a relation where the target endpoint is marked as required
                throw ExceptionUtils.handleException(e, () -> {
                    var targetId = dslContext.select(targetRef)
                            .from(table)
                            .where(sourceRef.eq(id.getValue()))
                            .fetchSingle(targetRef);
                    assert targetId != null;
                    return new EntityLinkedByRequiredRelationException(relation, id, EntityId.of(targetId));
                });
            }
        }

    }
}
