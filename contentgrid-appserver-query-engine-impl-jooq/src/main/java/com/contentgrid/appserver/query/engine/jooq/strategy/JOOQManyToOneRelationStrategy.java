package com.contentgrid.appserver.query.engine.jooq.strategy;

import com.contentgrid.appserver.application.model.Application;
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

final class JOOQManyToOneRelationStrategy extends JOOQXToOneRelationStrategy<ManyToOneRelation> implements
        HasSourceTableColumnRef<ManyToOneRelation> {

    @Override
    public Table<?> getTable(Application application, ManyToOneRelation relation) {
        return JOOQUtils.resolveTable(application.getRelationSourceEntity(relation));
    }

    @Override
    public Field<UUID> getSourceRef(Application application, ManyToOneRelation relation) {
        return getPrimaryKey(application, relation);
    }

    @Override
    public Field<UUID> getTargetRef(Application application, ManyToOneRelation relation) {
        return getForeignKey(application, relation);
    }

    @Override
    protected Field<UUID> getPrimaryKey(Application application, ManyToOneRelation relation) {
        return JOOQUtils.resolvePrimaryKey(application.getRelationSourceEntity(relation));
    }

    @Override
    protected Field<UUID> getForeignKey(Application application, ManyToOneRelation relation) {
        return (Field<UUID>) JOOQUtils.resolveField(relation.getTargetReference(), application.getRelationTargetEntity(relation).getPrimaryKey()
                .getType(), relation.getSourceEndPoint().isRequired());
    }

    @Override
    protected Entity getForeignEntity(Application application, ManyToOneRelation relation) {
        return application.getRelationTargetEntity(relation);
    }

    @Override
    public void create(DSLContext dslContext, Application application, ManyToOneRelation relation, EntityId id, EntityId targetId) {
        var table = getTable(application, relation);
        var sourceRef = getSourceRef(application, relation);
        var targetRef = getTargetRef(application, relation);

        try {
            var updated = dslContext.update(table)
                    .set(targetRef, targetId.getValue())
                    .where(sourceRef.eq(id.getValue()))
                    .execute();

            if (updated == 0) {
                throw new EntityIdNotFoundException(relation.getSourceEndPoint().getEntity(), id);
            }
        } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
            throw new ConstraintViolationException(e.getMessage(), e); // also thrown when foreign key was not found
        }
    }

    @Override
    public Field<UUID> getSourceTableColumnRef(Application application, ManyToOneRelation relation) {
        return getForeignKey(application, relation);
    }
}
