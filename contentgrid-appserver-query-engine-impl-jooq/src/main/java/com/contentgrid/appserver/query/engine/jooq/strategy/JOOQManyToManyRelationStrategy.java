package com.contentgrid.appserver.query.engine.jooq.strategy;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.query.engine.api.exception.ConstraintViolationException;
import com.contentgrid.appserver.query.engine.api.exception.InvalidSqlException;
import com.contentgrid.appserver.query.engine.api.exception.RelationLinkNotFoundException;
import com.contentgrid.appserver.query.engine.jooq.JOOQUtils;
import java.util.Set;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.exception.IntegrityConstraintViolationException;
import org.jooq.impl.DSL;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.BadSqlGrammarException;

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

        try {
            dslContext.createTable(joinTable)
                    .columns(sourceRef, targetRef)
                    .primaryKey(sourceRef, targetRef)
                    .constraint(DSL.foreignKey(sourceRef).references(sourceTable, sourcePrimaryKey))
                    .constraint(DSL.foreignKey(targetRef).references(targetTable, targetPrimaryKey))
                    .execute();
        } catch (BadSqlGrammarException e) {
            throw new InvalidSqlException(e.getMessage(), e);
        }
    }

    @Override
    public void destroy(DSLContext dslContext, Application application, ManyToManyRelation relation) {
        var table = getTable(application, relation);
        try {
            dslContext.dropTable(table).execute();
        } catch (BadSqlGrammarException e) {
            throw new InvalidSqlException(e.getMessage(), e);
        }
    }

    @Override
    public void add(DSLContext dslContext, Application application, ManyToManyRelation relation, EntityId id, Set<EntityId> targetIds) {
        var table = getTable(application, relation);
        var sourceRef = getSourceRef(application, relation);
        var targetRef = getTargetRef(application, relation);
        var step = dslContext.insertInto(table, sourceRef, targetRef);

        for (var targetId : targetIds) {
            step = step.values(id.getValue(), targetId.getValue());
        }

        try {
            step.execute();
        } catch (DuplicateKeyException e) {
            throw new ConstraintViolationException("One of the provided references already linked with provided id", e);
        } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
            throw new ConstraintViolationException(e.getMessage(), e); // provided source id could not exist
        }
    }

    @Override
    public void remove(DSLContext dslContext, Application application, ManyToManyRelation relation, EntityId id, Set<EntityId> targetIds) {
        var table = getTable(application, relation);
        var sourceRef = getSourceRef(application, relation);
        var targetRef = getTargetRef(application, relation);
        var refs = targetIds.stream()
                .map(EntityId::getValue)
                .toList();

        var deleted = dslContext.deleteFrom(table)
                .where(DSL.and(sourceRef.eq(id.getValue()), targetRef.in(refs)))
                .returning(targetRef)
                .fetchSet(targetRef);

        checkModifiedItems(refs, deleted, targetId -> new RelationLinkNotFoundException(relation, id, targetId));

    }

    @Override
    public void delete(DSLContext dslContext, Application application, ManyToManyRelation relation, EntityId id) {
        var table = getTable(application, relation);
        var sourceRef = getSourceRef(application, relation);

        dslContext.deleteFrom(table)
                .where(sourceRef.eq(id.getValue()))
                .execute();
    }

    @Override
    public void deleteAll(DSLContext dslContext, Application application, ManyToManyRelation relation) {
        dslContext.deleteFrom(getTable(application, relation)).execute();
    }
}
