package com.contentgrid.appserver.query.engine.jooq.strategy;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.domain.values.RelationIdentity;
import com.contentgrid.appserver.query.engine.api.exception.EntityIdNotFoundException;
import com.contentgrid.appserver.query.engine.api.exception.RelationLinkNotFoundException;
import com.contentgrid.appserver.query.engine.api.exception.RelationTargetNotFoundException;
import com.contentgrid.appserver.query.engine.jooq.DslContextUtils;
import com.contentgrid.appserver.query.engine.jooq.ExceptionUtils;
import com.contentgrid.appserver.query.engine.jooq.JOOQUtils;
import com.contentgrid.appserver.query.engine.jooq.PostgresqlErrorType;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

final class JOOQManyToManyRelationStrategy extends JOOQXToManyRelationStrategy<ManyToManyRelation> {

    @Override
    public Table<?> getTable(Application application, ManyToManyRelation relation) {
        return JOOQUtils.resolveTable(relation.getJoinTable());
    }

    @Override
    public Field<UUID> getSourceRef(Application application, ManyToManyRelation relation) {
        return (Field<UUID>) JOOQUtils.resolveField(relation.getSourceReference(), application.getRelationSourceEntity(relation).getPrimaryKey()
                .getType(), true);
    }

    @Override
    public Field<UUID> getTargetRef(Application application, ManyToManyRelation relation) {
        return (Field<UUID>) JOOQUtils.resolveField(relation.getTargetReference(), application.getRelationTargetEntity(relation).getPrimaryKey()
                .getType(), true);
    }

    @Override
    public void make(DSLContext dslContext, Application application, ManyToManyRelation relation) {
        var joinTable = getTable(application, relation);
        var sourceRef = getSourceRef(application, relation);
        var targetRef = getTargetRef(application, relation);
        var sourceTable = JOOQUtils.resolveTable(application.getRelationSourceEntity(relation));
        var targetTable = JOOQUtils.resolveTable(application.getRelationTargetEntity(relation));
        var sourcePrimaryKey = JOOQUtils.resolvePrimaryKey(application.getRelationSourceEntity(relation));
        var targetPrimaryKey = JOOQUtils.resolvePrimaryKey(application.getRelationTargetEntity(relation));

        dslContext.createTableIfNotExists(joinTable)
                .columns(sourceRef, targetRef)
                .primaryKey(sourceRef, targetRef)
                .constraint(DSL.foreignKey(sourceRef).references(sourceTable, sourcePrimaryKey))
                .constraint(DSL.foreignKey(targetRef).references(targetTable, targetPrimaryKey))
                .execute();
    }

    @Override
    public void destroy(DSLContext dslContext, Application application, ManyToManyRelation relation) {
        var table = getTable(application, relation);
        dslContext.dropTableIfExists(table).execute();
    }

    @Override
    public void add(DSLContext dslContext, Application application, ManyToManyRelation relation, EntityIdentity sourceIdentity, Set<EntityIdentity> targetIdentities) {
        var table = getTable(application, relation);
        var sourceRef = getSourceRef(application, relation);
        var targetRef = getTargetRef(application, relation);

        try {
            DslContextUtils.executeInSavepoint(dslContext, () -> {
                var step = dslContext.insertInto(table, sourceRef, targetRef);

                for (var targetIdentity : targetIdentities) {
                    step = step.values(sourceIdentity.getEntityId().getValue(), targetIdentity.getEntityId().getValue());
                }
                return step.onDuplicateKeyIgnore().execute();
            });
        } catch (DataAccessException e) {
            if (PostgresqlErrorType.from(e).is(PostgresqlErrorType.FOREIGN_KEY_CONSTRAINT_VIOLATION)) {
                // In a foreign key violation, we need to determine what are the non-existing items.
                // We can save a query by only checking the target IDs. If they are all present, the non-existing side must be the (singular) source side.
                throw ExceptionUtils.handleException(e, () -> {
                    var targetEntityName = relation.getTargetEndPoint().getEntity();
                    var targetEntity = application.getRequiredEntityByName(targetEntityName);
                    var targetTableName = JOOQUtils.resolveTable(targetEntity);
                    var targetPrimaryKey = JOOQUtils.resolvePrimaryKey(targetEntity);

                    var targetUuids = targetIdentities.stream().map(t -> t.getEntityId().getValue()).collect(Collectors.toSet());
                    var existingTargetUuids = dslContext.select(targetPrimaryKey)
                            .from(targetTableName)
                            .where(targetPrimaryKey.in(targetUuids))
                            .fetch(targetPrimaryKey);
                    targetUuids.removeAll(existingTargetUuids);

                    return ExceptionUtils.<UUID, EntityIdNotFoundException>createMultiple(targetUuids, targetUuid -> new RelationTargetNotFoundException(
                                    EntityIdentity.forEntity(targetEntityName, EntityId.of(targetUuid)),
                                    RelationIdentity.forRelation(sourceIdentity, relation.getSourceEndPoint().getName())
                            ))
                            // If all target entities are present, targetUuids will be empty, so it must be the source entity that does not exist
                            .orElseGet(() -> new EntityIdNotFoundException(sourceIdentity));
                });
            }
            throw e;
        }
    }

    @Override
    public void remove(DSLContext dslContext, Application application, ManyToManyRelation relation, EntityIdentity sourceIdentity, Set<EntityIdentity> targetIdentities) {
        var table = getTable(application, relation);
        var sourceRef = getSourceRef(application, relation);
        var targetRef = getTargetRef(application, relation);
        var refs = targetIdentities.stream()
                .map(t -> t.getEntityId().getValue())
                .toList();

        var deleted = dslContext.deleteFrom(table)
                .where(DSL.and(sourceRef.eq(sourceIdentity.getEntityId().getValue()), targetRef.in(refs)))
                .returning(targetRef)
                .fetchSet(targetRef);

        checkModifiedItems(refs, deleted, targetId -> new RelationLinkNotFoundException(relation, sourceIdentity.getEntityId(), targetId));

    }

    @Override
    public void delete(DSLContext dslContext, Application application, ManyToManyRelation relation, EntityIdentity sourceIdentity) {
        var table = getTable(application, relation);
        var sourceRef = getSourceRef(application, relation);

        dslContext.deleteFrom(table)
                .where(sourceRef.eq(sourceIdentity.getEntityId().getValue()))
                .execute();
    }

    @Override
    public void deleteAll(DSLContext dslContext, Application application, ManyToManyRelation relation) {
        dslContext.deleteFrom(getTable(application, relation)).execute();
    }
}
