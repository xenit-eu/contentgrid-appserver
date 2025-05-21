package com.contentgrid.appserver.query.engine.jooq.strategy;

import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.relations.SourceOneToOneRelation;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class JOOQSourceOneToOneRelationStrategy extends JOOQXToOneRelationStrategy<SourceOneToOneRelation> {

    @Override
    protected Table<?> getTable(SourceOneToOneRelation relation) {
        return JOOQUtils.resolveTable(relation.getSourceEndPoint().getEntity());
    }

    @Override
    protected Field<UUID> getSourceRef(SourceOneToOneRelation relation) {
        return getPrimaryKey(relation);
    }

    @Override
    protected Field<UUID> getTargetRef(SourceOneToOneRelation relation) {
        return getForeignKey(relation);
    }

    @Override
    protected Field<UUID> getPrimaryKey(SourceOneToOneRelation relation) {
        return JOOQUtils.resolvePrimaryKey(relation.getSourceEndPoint().getEntity());
    }

    @Override
    protected Field<UUID> getForeignKey(SourceOneToOneRelation relation) {
        return (Field<UUID>) JOOQUtils.resolveField(relation.getTargetReference(), relation.getTargetEndPoint().getEntity().getPrimaryKey()
                .getType(), relation.getSourceEndPoint().isRequired());
    }

    @Override
    protected Entity getForeignEntity(SourceOneToOneRelation relation) {
        return relation.getTargetEndPoint().getEntity();
    }

    @Override
    public void make(DSLContext dslContext, SourceOneToOneRelation relation) {
        super.make(dslContext, relation);

        // Make column unique
        dslContext.alterTable(getTable(relation))
                .add(DSL.unique(getForeignKey(relation)))
                .execute();
    }

    @Override
    public void create(DSLContext dslContext, SourceOneToOneRelation relation, EntityId id,
            XToOneRelationData data) {
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
        } catch (DuplicateKeyException e) {
            throw new ConstraintViolationException("Target %s already linked".formatted(data.getRef()), e);
        } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
            throw new ConstraintViolationException(e.getMessage(), e); // also thrown when foreign key was not found
        }
    }
}
