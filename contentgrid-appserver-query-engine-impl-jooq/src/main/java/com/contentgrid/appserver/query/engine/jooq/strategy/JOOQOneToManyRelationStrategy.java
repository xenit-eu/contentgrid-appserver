package com.contentgrid.appserver.query.engine.jooq.strategy;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.domain.values.RelationIdentity;
import com.contentgrid.appserver.query.engine.api.exception.BlindRelationOverwriteException;
import com.contentgrid.appserver.query.engine.api.exception.EntityIdNotFoundException;
import com.contentgrid.appserver.query.engine.api.exception.EntityLinkedByRequiredRelationException;
import com.contentgrid.appserver.query.engine.api.exception.RelationLinkNotFoundException;
import com.contentgrid.appserver.query.engine.api.exception.RelationTargetNotFoundException;
import com.contentgrid.appserver.query.engine.jooq.DslContextUtils;
import com.contentgrid.appserver.query.engine.jooq.ExceptionUtils;
import com.contentgrid.appserver.query.engine.jooq.JOOQUtils;
import com.contentgrid.appserver.query.engine.jooq.PostgresqlErrorType;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

final class JOOQOneToManyRelationStrategy extends JOOQXToManyRelationStrategy<OneToManyRelation> {

    @Override
    public Table<?> getTable(Application application, OneToManyRelation relation) {
        return JOOQUtils.resolveTable(application, application.getRelationTargetEntity(relation));
    }

    @Override
    public Field<UUID> getSourceRef(Application application, OneToManyRelation relation) {
        return (Field<UUID>) JOOQUtils.resolveField(relation.getSourceReference(), application.getRelationSourceEntity(relation).getPrimaryKey()
                .getType(), relation.getTargetEndPoint().isRequired());
    }

    @Override
    public Field<UUID> getTargetRef(Application application, OneToManyRelation relation) {
        return JOOQUtils.resolvePrimaryKey(application.getRelationTargetEntity(relation));
    }

    @Override
    public void make(DSLContext dslContext, Application application, OneToManyRelation relation) {
        var targetTable = getTable(application, relation);
        var sourceRef = getSourceRef(application, relation);
        var sourceTable = JOOQUtils.resolveTable(application, application.getRelationSourceEntity(relation));
        var sourcePrimaryKey = JOOQUtils.resolvePrimaryKey(application.getRelationSourceEntity(relation));

        dslContext.alterTable(targetTable)
                .addIfNotExists(sourceRef)
                .execute();
        dslContext.alterTable(targetTable)
                .add(DSL.foreignKey(sourceRef).references(sourceTable, sourcePrimaryKey))
                .execute();
    }

    @Override
    public void destroy(DSLContext dslContext, Application application, OneToManyRelation relation) {
        var table = getTable(application, relation);
        var sourceRef = getSourceRef(application, relation);
        dslContext.alterTable(table).dropColumnIfExists(sourceRef).execute();
    }

    private Collection<UUID> getRefs(Set<EntityIdentity> data) {
        return data.stream().map(e -> e.getEntityId().getValue()).toList();
    }

    @Override
    public void add(DSLContext dslContext, Application application, OneToManyRelation relation, EntityIdentity sourceIdentity,
            Set<EntityIdentity> targetIdentities) {
        var table = getTable(application, relation);
        var sourceRef = getSourceRef(application, relation);
        var targetRef = getTargetRef(application, relation);
        var refs = getRefs(targetIdentities);

        try {
            var updatedItems = dslContext.update(table)
                    .set(sourceRef, DSL.coalesce(sourceRef, sourceIdentity.getEntityId().getValue())) // Only use the new value when sourceRef was null, otherwise it's a blind relation overwrite
                    .where(targetRef.in(refs))
                    .returning(sourceRef, targetRef)
                    .fetch();


            var maybeException = ExceptionUtils.createMultiple(updatedItems.stream()
                            .filter(updatedItem -> !Objects.equals(updatedItem.get(sourceRef), sourceIdentity.getEntityId().getValue())),
                    item -> new BlindRelationOverwriteException(
                            RelationIdentity.forRelation(sourceIdentity, relation.getSourceEndPoint().getName()),
                            EntityIdentity.forEntity(
                                    relation.getTargetEndPoint().getEntity(),
                                    EntityId.of(item.get(targetRef))
                            ),
                            EntityIdentity.forEntity(
                                    relation.getSourceEndPoint().getEntity(),
                                    EntityId.of(item.get(sourceRef))
                            )
                    ));

            if(maybeException.isPresent()) {
                throw maybeException.get();
            }

            checkModifiedItems(
                    refs,
                    updatedItems.stream().map(i -> i.get(targetRef)).collect(Collectors.toSet()),
                    targetId -> new RelationTargetNotFoundException(
                            EntityIdentity.forEntity(relation.getTargetEndPoint().getEntity(), targetId),
                            RelationIdentity.forRelation(sourceIdentity, relation.getSourceEndPoint().getName())
                    )
            );

        } catch (DataAccessException e) {
            if(PostgresqlErrorType.from(e).is(PostgresqlErrorType.FOREIGN_KEY_CONSTRAINT_VIOLATION)) {
                // Foreign key violation indicates that the source entity id doesn't exist.
                // (We tried to insert into a column that has an FK to the source entity)
                throw ExceptionUtils.handleException(e, () -> new EntityIdNotFoundException(sourceIdentity));
            }
            throw e;
        }
    }

    @Override
    public void remove(DSLContext dslContext, Application application, OneToManyRelation relation, EntityIdentity sourceIdentity,
            Set<EntityIdentity> targetIdentities) {
        var table = getTable(application, relation);
        var sourceRef = getSourceRef(application, relation);
        var targetRef = getTargetRef(application, relation);
        var refs = getRefs(targetIdentities);

        try {
            var updated = dslContext.update(table)
                    .set(sourceRef, (UUID) null)
                    .where(DSL.and(sourceRef.eq(sourceIdentity.getEntityId().getValue()), targetRef.in(refs)))
                    .returning(targetRef)
                    .fetchSet(targetRef);

            checkModifiedItems(refs, updated, targetId -> new RelationLinkNotFoundException(relation, sourceIdentity.getEntityId(), targetId));

        } catch (DataAccessException e) {
            if (PostgresqlErrorType.from(e).is(PostgresqlErrorType.NOT_NULL_CONSTRAINT_VIOLATION) && relation.getTargetEndPoint().isRequired()) {
                // If sourceRef is required to be present in one case, it will obviously be required in all cases
                throw ExceptionUtils.handleException(e, () -> ExceptionUtils.createMultiple(targetIdentities,
                                targetIdentity -> new EntityLinkedByRequiredRelationException(relation, sourceIdentity.getEntityId(), targetIdentity.getEntityId()))
                        // This shouldn't happen, we can't get a non-null constraint violation if nothing is being removed
                        .orElseThrow());
            } else {
                throw e;
            }
        }
    }

    @Override
    public void delete(DSLContext dslContext, Application application, OneToManyRelation relation, EntityIdentity sourceIdentity) {
        var table = getTable(application, relation);
        var sourceRef = getSourceRef(application, relation);
        var targetRef = getTargetRef(application, relation);

        try {
            // This query needs to be isolated in a savepoint, so we can run a query in the exception handler
            DslContextUtils.executeInSavepoint(dslContext, () -> dslContext.update(table)
                    .set(sourceRef, (UUID) null)
                    .where(sourceRef.eq(sourceIdentity.getEntityId().getValue()))
                    .execute()
            );
        } catch (DataAccessException e) {
            if(PostgresqlErrorType.from(e).is(PostgresqlErrorType.NOT_NULL_CONSTRAINT_VIOLATION) && relation.getTargetEndPoint().isRequired()) {
                throw ExceptionUtils.handleException(e, () -> {
                    // Fetch one example target ID that is covered by this constraint
                    var targetId = dslContext.select(targetRef)
                            .from(table)
                            .where(sourceRef.eq(sourceIdentity.getEntityId().getValue()))
                            .limit(1)
                            .fetchSingle(targetRef);
                    // If the original exception is being thrown, there must be at least one matching row for the where clause.
                    // Given the definition that target is *required*, it also can't be null. So this assert is just here to make
                    // the linter happy.
                    assert targetId != null;
                    return new EntityLinkedByRequiredRelationException(relation, sourceIdentity.getEntityId(), EntityId.of(targetId));
                });
            }
            throw e;
        }
    }

    @Override
    public void deleteAll(DSLContext dslContext, Application application, OneToManyRelation relation) {
        var table = getTable(application, relation);
        var sourceRef = getSourceRef(application, relation);

        dslContext.update(table)
                .set(sourceRef, (UUID) null)
                .execute();
    }
}
