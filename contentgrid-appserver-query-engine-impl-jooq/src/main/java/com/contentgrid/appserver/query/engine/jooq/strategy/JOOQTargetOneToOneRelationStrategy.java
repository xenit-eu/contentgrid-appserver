package com.contentgrid.appserver.query.engine.jooq.strategy;

import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.relations.TargetOneToOneRelation;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.query.engine.api.data.XToOneRelationData;
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
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class JOOQTargetOneToOneRelationStrategy extends JOOQXToOneRelationStrategy<TargetOneToOneRelation> {

    @Override
    protected Table<?> getTable(TargetOneToOneRelation relation) {
        return JOOQUtils.resolveTable(relation.getTargetEndPoint().getEntity());
    }

    @Override
    protected Field<UUID> getSourceRef(TargetOneToOneRelation relation) {
        return getForeignKey(relation);
    }

    @Override
    protected Field<UUID> getTargetRef(TargetOneToOneRelation relation) {
        return getPrimaryKey(relation);
    }

    @Override
    protected Field<UUID> getPrimaryKey(TargetOneToOneRelation relation) {
        return JOOQUtils.resolvePrimaryKey(relation.getTargetEndPoint().getEntity());
    }

    @Override
    protected Field<UUID> getForeignKey(TargetOneToOneRelation relation) {
        return (Field<UUID>) JOOQUtils.resolveField(relation.getSourceReference(), relation.getSourceEndPoint().getEntity().getPrimaryKey()
                .getType(), relation.getTargetEndPoint().isRequired());
    }

    @Override
    protected Entity getForeignEntity(TargetOneToOneRelation relation) {
        return relation.getSourceEndPoint().getEntity();
    }

    @Override
    public void make(DSLContext dslContext, TargetOneToOneRelation relation) {
        super.make(dslContext, relation);

        // Make column unique
        dslContext.alterTable(getTable(relation))
                .add(DSL.unique(getForeignKey(relation)))
                .execute();
    }

    @Override
    public void create(DSLContext dslContext, TargetOneToOneRelation relation, EntityId id,
            XToOneRelationData data) {
        var table = getTable(relation);
        var sourceRef = getSourceRef(relation);
        var targetRef = getTargetRef(relation);

        try {
            var updated = dslContext.update(table)
                    .set(sourceRef, id.getValue())
                    .where(targetRef.eq(data.getRef().getValue()))
                    .execute();

            if (updated == 0) {
                throw new EntityNotFoundException(
                        "Entity with primary key '%s' not found".formatted(data.getRef()));
            }
        } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
            throw new ConstraintViolationException(e.getMessage(), e); // also thrown when foreign key was not found
        }
    }
}
