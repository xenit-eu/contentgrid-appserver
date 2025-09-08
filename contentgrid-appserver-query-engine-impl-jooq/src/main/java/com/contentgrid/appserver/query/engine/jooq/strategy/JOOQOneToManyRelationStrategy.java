package com.contentgrid.appserver.query.engine.jooq.strategy;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.query.engine.api.exception.ConstraintViolationException;
import com.contentgrid.appserver.query.engine.api.exception.EntityIdNotFoundException;
import com.contentgrid.appserver.query.engine.api.exception.InvalidSqlException;
import com.contentgrid.appserver.query.engine.api.exception.RelationLinkNotFoundException;
import com.contentgrid.appserver.query.engine.jooq.JOOQUtils;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.exception.IntegrityConstraintViolationException;
import org.jooq.impl.DSL;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.BadSqlGrammarException;

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

        try {
            dslContext.alterTable(targetTable)
                    .add(sourceRef, DSL.foreignKey(sourceRef).references(sourceTable, sourcePrimaryKey))
                    .execute();
        } catch (BadSqlGrammarException e) {
            throw new InvalidSqlException(e.getMessage(), e);
        }
    }

    @Override
    public void destroy(DSLContext dslContext, Application application, OneToManyRelation relation) {
        var table = getTable(application, relation);
        var sourceRef = getSourceRef(application, relation);
        try {
            dslContext.alterTable(table).dropColumnIfExists(sourceRef).execute();
        } catch (BadSqlGrammarException e) {
            throw new InvalidSqlException(e.getMessage(), e); // table could not exist
        }
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
            var updated = dslContext.update(table)
                    .set(sourceRef, id.getValue())
                    .where(targetRef.in(refs))
                    .returning(targetRef)
                    .fetchSet(targetRef);

            checkModifiedItems(refs, updated, targetId -> new EntityIdNotFoundException(relation.getTargetEndPoint().getEntity(),
                    targetId));

        } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
            throw new ConstraintViolationException(e.getMessage(), e); // provided source id could not exist
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

        } catch (IntegrityConstraintViolationException | DataIntegrityViolationException e) {
            if (relation.getTargetEndPoint().isRequired()) {
                throw new ConstraintViolationException(
                        "Cannot remove references from relation '%s' because inverse many-to-one relation is required"
                                .formatted(relation.getSourceEndPoint().getName()), e);
            } else {
                throw new ConstraintViolationException(e.getMessage(), e);
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
        } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
            throw new ConstraintViolationException(e.getMessage(), e); // inverse could be required
        }
    }

    @Override
    public void deleteAll(DSLContext dslContext, Application application, OneToManyRelation relation) {
        var table = getTable(application, relation);
        var sourceRef = getSourceRef(application, relation);

        try {
            dslContext.update(table)
                    .set(sourceRef, (UUID) null)
                    .execute();
        } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
            throw new ConstraintViolationException(e.getMessage(), e); // inverse could be required
        }
    }
}
