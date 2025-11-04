package com.contentgrid.appserver.query.engine.jooq.strategy;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.domain.values.RelationIdentity;
import com.contentgrid.appserver.query.engine.api.exception.BlindRelationOverwriteException;
import com.contentgrid.appserver.query.engine.api.exception.ConstraintViolationException;
import com.contentgrid.appserver.query.engine.api.exception.EntityIdNotFoundException;
import com.contentgrid.appserver.query.engine.api.exception.RelationLinkNotFoundException;
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
        return JOOQUtils.resolveTable(application.getRelationTargetEntity(relation));
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
        var sourceTable = JOOQUtils.resolveTable(application.getRelationSourceEntity(relation));
        var sourcePrimaryKey = JOOQUtils.resolvePrimaryKey(application.getRelationSourceEntity(relation));

        dslContext.alterTable(targetTable)
                .add(sourceRef, DSL.foreignKey(sourceRef).references(sourceTable, sourcePrimaryKey))
                .execute();
    }

    @Override
    public void destroy(DSLContext dslContext, Application application, OneToManyRelation relation) {
        var table = getTable(application, relation);
        var sourceRef = getSourceRef(application, relation);
        dslContext.alterTable(table).dropColumnIfExists(sourceRef).execute();
    }

    private Collection<UUID> getRefs(Set<EntityId> data) {
        return data.stream().map(EntityId::getValue).toList();
    }

    @Override
    public void add(DSLContext dslContext, Application application, OneToManyRelation relation, EntityId id,
            Set<EntityId> targetIds) {
        var table = getTable(application, relation);
        var sourceRef = getSourceRef(application, relation);
        var targetRef = getTargetRef(application, relation);
        var refs = getRefs(targetIds);

        try {
            var updatedItems = dslContext.update(table)
                    .set(sourceRef, DSL.coalesce(sourceRef, id.getValue())) // Only use the new value when sourceRef was null
                    .where(targetRef.in(refs))
                    .returning(sourceRef, targetRef)
                    .fetch();

            var maybeException = updatedItems.stream()
                    .filter(updatedItem -> !Objects.equals(updatedItem.get(sourceRef), id.getValue()))
                    .map(item -> new BlindRelationOverwriteException(
                            RelationIdentity.forRelation(
                                    relation.getTargetEndPoint().getEntity(),
                                    EntityId.of(item.get(targetRef)),
                                    relation.getTargetEndPoint().getName()
                            ),
                            EntityIdentity.forEntity(
                                    relation.getSourceEndPoint().getEntity(),
                                    EntityId.of(item.get(sourceRef))
                            )
                    ))
                    .reduce((a, b) -> {
                        a.addSuppressed(b);
                        return a;
                    });

            if(maybeException.isPresent()) {
                throw maybeException.get();
            }

            checkModifiedItems(
                    refs,
                    updatedItems.stream().map(i -> i.get(targetRef)).collect(Collectors.toSet()),
                    targetId -> new EntityIdNotFoundException(relation.getTargetEndPoint().getEntity(), targetId)
            );

        } catch (DataAccessException e) {
            // TODO: handle FK violation when source id does not exist
            throw e;
        }
    }

    @Override
    public void remove(DSLContext dslContext, Application application, OneToManyRelation relation, EntityId id,
            Set<EntityId> targetIds) {
        var table = getTable(application, relation);
        var sourceRef = getSourceRef(application, relation);
        var targetRef = getTargetRef(application, relation);
        var refs = getRefs(targetIds);

        try {
            var updated = dslContext.update(table)
                    .set(sourceRef, (UUID) null)
                    .where(DSL.and(sourceRef.eq(id.getValue()), targetRef.in(refs)))
                    .returning(targetRef)
                    .fetchSet(targetRef);

            checkModifiedItems(refs, updated, targetId -> new RelationLinkNotFoundException(relation, id, targetId));

        } catch (DataAccessException e) {
            if (PostgresqlErrorType.from(e).is(PostgresqlErrorType.NOT_NULL_CONSTRAINT_VIOLATION) && relation.getTargetEndPoint().isRequired()) {
                throw new ConstraintViolationException(
                        "Cannot remove references from relation '%s' because inverse many-to-one relation is required"
                                .formatted(relation.getSourceEndPoint().getName()), e);
            } else {
                throw e;
            }
        }
    }

    @Override
    public void delete(DSLContext dslContext, Application application, OneToManyRelation relation, EntityId id) {
        var table = getTable(application, relation);
        var sourceRef = getSourceRef(application, relation);

        try {
            dslContext.update(table)
                    .set(sourceRef, (UUID) null)
                    .where(sourceRef.eq(id.getValue()))
                    .execute();
        } catch (DataAccessException e) {
            // TODO: handle non-null constraint violation
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
