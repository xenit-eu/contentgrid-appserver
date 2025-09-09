package com.contentgrid.appserver.query.engine.jooq.strategy;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.relations.SourceOneToOneRelation;
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
import org.springframework.dao.DuplicateKeyException;

final class JOOQSourceOneToOneRelationStrategy extends JOOQXToOneRelationStrategy<SourceOneToOneRelation> implements HasSourceTableColumnRef<SourceOneToOneRelation> {

    @Override
    public Table<?> getTable(Application application, SourceOneToOneRelation relation) {
        return JOOQUtils.resolveTable(application.getRelationSourceEntity(relation));
    }

    @Override
    public Field<UUID> getSourceRef(Application application, SourceOneToOneRelation relation) {
        return getPrimaryKey(application, relation);
    }

    @Override
    public Field<UUID> getTargetRef(Application application, SourceOneToOneRelation relation) {
        return getForeignKey(application, relation);
    }

    @Override
    protected Field<UUID> getPrimaryKey(Application application, SourceOneToOneRelation relation) {
        return JOOQUtils.resolvePrimaryKey(application.getRelationSourceEntity(relation));
    }

    @Override
    protected Field<UUID> getForeignKey(Application application, SourceOneToOneRelation relation) {
        return (Field<UUID>) JOOQUtils.resolveField(relation.getTargetReference(), application.getRelationTargetEntity(relation).getPrimaryKey()
                .getType(), relation.getSourceEndPoint().isRequired());
    }

    @Override
    protected Entity getForeignEntity(Application application, SourceOneToOneRelation relation) {
        return application.getRelationTargetEntity(relation);
    }

    @Override
    public void make(DSLContext dslContext, Application application, SourceOneToOneRelation relation) {
        super.make(dslContext, application, relation);

        // Make column unique
        dslContext.alterTable(getTable(application, relation))
                .add(DSL.unique(getForeignKey(application, relation)))
                .execute();
    }

    @Override
    public void create(DSLContext dslContext, Application application, SourceOneToOneRelation relation, EntityId id,
            EntityId targetId) {
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
        } catch (DuplicateKeyException e) {
            throw new ConstraintViolationException("Target %s already linked".formatted(targetId), e);
        } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
            throw new ConstraintViolationException(e.getMessage(), e); // also thrown when foreign key was not found
        }
    }

    @Override
    public Field<UUID> getSourceTableColumnRef(Application application, SourceOneToOneRelation relation) {
        return getForeignKey(application, relation);
    }
}
