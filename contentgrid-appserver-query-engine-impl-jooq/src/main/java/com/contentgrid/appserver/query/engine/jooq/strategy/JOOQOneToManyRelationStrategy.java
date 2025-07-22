package com.contentgrid.appserver.query.engine.jooq.strategy;

import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.query.engine.api.data.XToManyRelationData;
import com.contentgrid.appserver.query.engine.api.exception.ConstraintViolationException;
import com.contentgrid.appserver.query.engine.api.exception.EntityNotFoundException;
import com.contentgrid.appserver.query.engine.api.exception.InvalidSqlException;
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
import org.springframework.transaction.annotation.Transactional;

@Transactional
public final class JOOQOneToManyRelationStrategy extends JOOQXToManyRelationStrategy<OneToManyRelation> {

    @Override
    public Table<?> getTable(OneToManyRelation relation) {
        return JOOQUtils.resolveTable(relation.getTargetEndPoint().getEntity());
    }

    @Override
    public Field<UUID> getSourceRef(OneToManyRelation relation) {
        return (Field<UUID>) JOOQUtils.resolveField(relation.getSourceReference(), relation.getSourceEndPoint().getEntity().getPrimaryKey()
                .getType(), relation.getTargetEndPoint().isRequired());
    }

    @Override
    public Field<UUID> getTargetRef(OneToManyRelation relation) {
        return JOOQUtils.resolvePrimaryKey(relation.getTargetEndPoint().getEntity());
    }

    @Override
    public void make(DSLContext dslContext, OneToManyRelation relation) {
        var targetTable = getTable(relation);
        var sourceRef = getSourceRef(relation);
        var sourceTable = JOOQUtils.resolveTable(relation.getSourceEndPoint().getEntity());
        var sourcePrimaryKey = JOOQUtils.resolvePrimaryKey(relation.getSourceEndPoint().getEntity());

        try {
            dslContext.alterTable(targetTable)
                    .add(sourceRef, DSL.foreignKey(sourceRef).references(sourceTable, sourcePrimaryKey))
                    .execute();
        } catch (BadSqlGrammarException e) {
            throw new InvalidSqlException(e.getMessage(), e);
        }
    }

    @Override
    public void destroy(DSLContext dslContext, OneToManyRelation relation) {
        var table = getTable(relation);
        var sourceRef = getSourceRef(relation);
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
    public void add(DSLContext dslContext, OneToManyRelation relation, EntityId id,
            Set<EntityId> targetIds) {
        var table = getTable(relation);
        var sourceRef = getSourceRef(relation);
        var targetRef = getTargetRef(relation);
        var refs = getRefs(targetIds);

        try {
            var updated = dslContext.update(table)
                    .set(sourceRef, id.getValue())
                    .where(targetRef.in(refs))
                    .execute();

            if (updated < refs.size()) {
                throw new EntityNotFoundException("Some entities from provided data not found");
            }
        } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
            throw new ConstraintViolationException(e.getMessage(), e); // provided source id could not exist
        }
    }

    @Override
    public void remove(DSLContext dslContext, OneToManyRelation relation, EntityId id,
            Set<EntityId> targetIds) {
        var table = getTable(relation);
        var sourceRef = getSourceRef(relation);
        var targetRef = getTargetRef(relation);
        var refs = getRefs(targetIds);

        try {
            var updated = dslContext.update(table)
                    .set(sourceRef, (UUID) null)
                    .where(DSL.and(sourceRef.eq(id.getValue()), targetRef.in(refs)))
                    .execute();

            if (updated < refs.size()) {
                throw new EntityNotFoundException(
                        "Entities provided that are not linked to entity '%s' with primary key '%s'"
                                .formatted(relation.getTargetEndPoint().getEntity(), id));
            }
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
    public void delete(DSLContext dslContext, OneToManyRelation relation, EntityId id) {
        var table = getTable(relation);
        var sourceRef = getSourceRef(relation);

        try {
            var updated = dslContext.update(table)
                    .set(sourceRef, (UUID) null)
                    .where(sourceRef.eq(id.getValue()))
                    .execute();

            if (updated == 0) {
                assertEntityExists(dslContext, relation.getSourceEndPoint().getEntity(), id);
            }
        } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
            throw new ConstraintViolationException(e.getMessage(), e); // inverse could be required
        }
    }

    @Override
    public void deleteAll(DSLContext dslContext, OneToManyRelation relation) {
        var table = getTable(relation);
        var sourceRef = getSourceRef(relation);

        try {
            dslContext.update(table)
                    .set(sourceRef, (UUID) null)
                    .execute();
        } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
            throw new ConstraintViolationException(e.getMessage(), e); // inverse could be required
        }
    }
}
