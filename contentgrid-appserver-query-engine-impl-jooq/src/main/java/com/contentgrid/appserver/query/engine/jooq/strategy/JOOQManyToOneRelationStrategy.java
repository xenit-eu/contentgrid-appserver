package com.contentgrid.appserver.query.engine.jooq.strategy;

import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.query.engine.api.exception.ConstraintViolationException;
import com.contentgrid.appserver.query.engine.api.exception.EntityIdNotFoundException;
import com.contentgrid.appserver.query.engine.jooq.JOOQUtils;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.exception.IntegrityConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public final class JOOQManyToOneRelationStrategy extends JOOQXToOneRelationStrategy<ManyToOneRelation> implements
        HasSourceTableColumnRef<ManyToOneRelation> {

    @Override
    public Table<?> getTable(ManyToOneRelation relation) {
        return JOOQUtils.resolveTable(relation.getSourceEndPoint().getEntity());
    }

    @Override
    public Field<UUID> getSourceRef(ManyToOneRelation relation) {
        return getPrimaryKey(relation);
    }

    @Override
    public Field<UUID> getTargetRef(ManyToOneRelation relation) {
        return getForeignKey(relation);
    }

    @Override
    protected Field<UUID> getPrimaryKey(ManyToOneRelation relation) {
        return JOOQUtils.resolvePrimaryKey(relation.getSourceEndPoint().getEntity());
    }

    @Override
    protected Field<UUID> getForeignKey(ManyToOneRelation relation) {
        return (Field<UUID>) JOOQUtils.resolveField(relation.getTargetReference(), relation.getTargetEndPoint().getEntity().getPrimaryKey()
                .getType(), relation.getSourceEndPoint().isRequired());
    }

    @Override
    protected Entity getForeignEntity(ManyToOneRelation relation) {
        return relation.getTargetEndPoint().getEntity();
    }

    @Override
    public void create(DSLContext dslContext, ManyToOneRelation relation, EntityId id, EntityId targetId) {
        var table = getTable(relation);
        var sourceRef = getSourceRef(relation);
        var targetRef = getTargetRef(relation);

        try {
            var updated = dslContext.update(table)
                    .set(targetRef, targetId.getValue())
                    .where(sourceRef.eq(id.getValue()))
                    .execute();

            if (updated == 0) {
                throw new EntityIdNotFoundException(relation.getSourceEndPoint().getEntity().getName(), id);
            }
        } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
            throw new ConstraintViolationException(e.getMessage(), e); // also thrown when foreign key was not found
        }
    }

    @Override
    public Field<UUID> getSourceTableColumnRef(ManyToOneRelation relation) {
        return getForeignKey(relation);
    }
}
