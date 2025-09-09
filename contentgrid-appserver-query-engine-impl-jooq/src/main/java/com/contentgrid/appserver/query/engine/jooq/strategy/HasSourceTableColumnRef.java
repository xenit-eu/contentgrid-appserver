package com.contentgrid.appserver.query.engine.jooq.strategy;

import com.contentgrid.appserver.application.model.relations.Relation;
import java.util.UUID;
import org.jooq.Field;

/**
 * Marks strategies that have a source column name and store the data in the source table
 */
public interface HasSourceTableColumnRef<R extends Relation> {
    Field<UUID> getSourceTableColumnRef(R relation);
}
