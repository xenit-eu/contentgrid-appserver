package com.contentgrid.appserver.query.engine.jooq.strategy;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.query.engine.jooq.JOOQUtils;
import java.util.UUID;
import org.jooq.Field;
import org.jooq.Table;

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
    public Field<UUID> getSourceTableColumnRef(Application application, ManyToOneRelation relation) {
        return getForeignKey(application, relation);
    }
}
