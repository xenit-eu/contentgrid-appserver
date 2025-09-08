package com.contentgrid.appserver.query.engine.jooq.strategy;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.relations.Relation;
import java.util.UUID;
import org.jooq.Field;

/**
 * Marks strategies that have a source column name and store the data in the source table
 */
public sealed interface HasSourceTableColumnRef<R extends Relation> extends JOOQRelationStrategy<R> permits
        JOOQManyToOneRelationStrategy, JOOQSourceOneToOneRelationStrategy {
    Field<UUID> getSourceTableColumnRef(Application application, R relation);
}
