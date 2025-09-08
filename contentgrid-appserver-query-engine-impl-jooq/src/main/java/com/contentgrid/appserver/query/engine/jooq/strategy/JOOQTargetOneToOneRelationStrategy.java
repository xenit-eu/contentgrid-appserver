package com.contentgrid.appserver.query.engine.jooq.strategy;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.relations.TargetOneToOneRelation;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.query.engine.api.exception.ConstraintViolationException;
import com.contentgrid.appserver.query.engine.api.exception.EntityIdNotFoundException;
import com.contentgrid.appserver.query.engine.jooq.JOOQUtils;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.exception.IntegrityConstraintViolationException;
import org.jooq.impl.DSL;
import org.springframework.dao.DataIntegrityViolationException;

final class JOOQTargetOneToOneRelationStrategy extends JOOQXToOneRelationStrategy<TargetOneToOneRelation> {

    @Override
    public Table<?> getTable(Application application, TargetOneToOneRelation relation) {
        return JOOQUtils.resolveTable(application.getRelationTargetEntity(relation));
    }

    @Override
    public Field<UUID> getSourceRef(Application application, TargetOneToOneRelation relation) {
        return getForeignKey(application, relation);
    }

    @Override
    public Field<UUID> getTargetRef(Application application, TargetOneToOneRelation relation) {
        return getPrimaryKey(application, relation);
    }

    @Override
    protected Field<UUID> getPrimaryKey(Application application, TargetOneToOneRelation relation) {
        return JOOQUtils.resolvePrimaryKey(application.getRelationTargetEntity(relation));
    }

    @Override
    protected Field<UUID> getForeignKey(Application application, TargetOneToOneRelation relation) {
        return (Field<UUID>) JOOQUtils.resolveField(relation.getSourceReference(), application.getRelationSourceEntity(relation).getPrimaryKey()
                .getType(), relation.getTargetEndPoint().isRequired());
    }

    @Override
    protected Entity getForeignEntity(Application application, TargetOneToOneRelation relation) {
        return application.getRelationSourceEntity(relation);
    }

    @Override
    public void make(DSLContext dslContext, Application application, TargetOneToOneRelation relation) {
        super.make(dslContext, application, relation);

        // Make column unique
        dslContext.alterTable(getTable(application, relation))
                .add(DSL.unique(getForeignKey(application, relation)))
                .execute();
    }

    @Override
    public void create(DSLContext dslContext, Application application, TargetOneToOneRelation relation, EntityId id,
            EntityId targetId) {
        var table = getTable(application, relation);
        var sourceRef = getSourceRef(application, relation);
        var targetRef = getTargetRef(application, relation);

        try {
            var updated = dslContext.update(table)
                    .set(sourceRef, id.getValue())
                    .where(targetRef.eq(targetId.getValue()))
                    .execute();

            if (updated == 0) {
                throw new EntityIdNotFoundException(relation.getTargetEndPoint().getEntity(), targetId);
            }
        } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
            throw new ConstraintViolationException(e.getMessage(), e); // also thrown when foreign key was not found
        }
    }
}
