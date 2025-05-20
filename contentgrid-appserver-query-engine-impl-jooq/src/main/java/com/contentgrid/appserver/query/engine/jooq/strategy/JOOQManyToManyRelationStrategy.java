package com.contentgrid.appserver.query.engine.jooq.strategy;

import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.query.engine.api.data.XToManyRelationData;
import com.contentgrid.appserver.query.engine.api.exception.ConstraintViolationException;
import com.contentgrid.appserver.query.engine.api.exception.EntityNotFoundException;
import com.contentgrid.appserver.query.engine.jooq.JOOQUtils;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.exception.IntegrityConstraintViolationException;
import org.jooq.impl.DSL;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class JOOQManyToManyRelationStrategy extends JOOQXToManyRelationStrategy<ManyToManyRelation> {

    @Override
    protected Table<?> getTable(ManyToManyRelation relation) {
        return JOOQUtils.resolveTable(relation.getJoinTable());
    }

    @Override
    protected Field<UUID> getSourceRef(ManyToManyRelation relation) {
        return (Field<UUID>) JOOQUtils.resolveField(relation.getSourceReference(), relation.getSourceEndPoint().getEntity().getPrimaryKey()
                .getType(), true);
    }

    @Override
    protected Field<UUID> getTargetRef(ManyToManyRelation relation) {
        return (Field<UUID>) JOOQUtils.resolveField(relation.getTargetReference(), relation.getTargetEndPoint().getEntity().getPrimaryKey()
                .getType(), true);
    }

    @Override
    public void add(DSLContext dslContext, ManyToManyRelation relation, EntityId id, XToManyRelationData data) {
        var table = getTable(relation);
        var sourceRef = getSourceRef(relation);
        var targetRef = getTargetRef(relation);
        var step = dslContext.insertInto(table, sourceRef, targetRef);

        for (var ref : data.getRefs()) {
            step = step.values(id.getValue(), ref.getValue());
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
    public void remove(DSLContext dslContext, ManyToManyRelation relation, EntityId id, XToManyRelationData data) {
        var table = getTable(relation);
        var sourceRef = getSourceRef(relation);
        var targetRef = getTargetRef(relation);
        var refs = data.getRefs().stream()
                .map(EntityId::getValue)
                .toList();

        var deleted = dslContext.deleteFrom(table)
                .where(DSL.and(sourceRef.eq(id.getValue()), targetRef.in(refs)))
                .execute();

        if (deleted < refs.size()) {
            throw new EntityNotFoundException("Some provided target entities of relation '%s' not found"
                    .formatted(data.getName()));
        }
    }

    @Override
    public void delete(DSLContext dslContext, ManyToManyRelation relation, EntityId id) {
        var table = getTable(relation);
        var sourceRef = getSourceRef(relation);

        var deleted = dslContext.deleteFrom(table)
                .where(sourceRef.eq(id.getValue()))
                .execute();

        if (deleted == 0) {
            assertEntityExists(dslContext, relation.getSourceEndPoint().getEntity(), id);
        }
    }

    @Override
    public void deleteAll(DSLContext dslContext, ManyToManyRelation relation) {
        dslContext.deleteFrom(getTable(relation)).execute();
    }
}
