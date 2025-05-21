package com.contentgrid.appserver.query.engine.jooq.strategy;

import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class JOOQManyToOneRelationStrategy extends JOOQXToOneRelationStrategy<ManyToOneRelation> {

    @Override
    protected Table<?> getTable(ManyToOneRelation relation) {
        return JOOQUtils.resolveTable(relation.getSourceEndPoint().getEntity());
    }

    @Override
    protected Field<UUID> getSourceRef(ManyToOneRelation relation) {
        return getPrimaryKey(relation);
    }

    @Override
    protected Field<UUID> getTargetRef(ManyToOneRelation relation) {
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
    public void create(DSLContext dslContext, ManyToOneRelation relation, EntityId id, XToOneRelationData data) {
        var table = getTable(relation);
        var sourceRef = getSourceRef(relation);
        var targetRef = getTargetRef(relation);

        try {
            var updated = dslContext.update(table)
                    .set(targetRef, data.getRef().getValue())
                    .where(sourceRef.eq(id.getValue()))
                    .execute();

            if (updated == 0) {
                throw new EntityNotFoundException(
                        "Entity with primary key '%s' not found".formatted(id));
            }
        } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
            throw new ConstraintViolationException(e.getMessage(), e); // also thrown when foreign key was not found
        }
    }
}
