package com.contentgrid.appserver.query.engine.jooq.count;

import com.contentgrid.appserver.domain.values.ItemCount;
import org.jooq.DSLContext;
import org.jooq.Select;

/**
 * A {@link JOOQCountStrategy} that performs an exact count.
 */
public class JOOQExactCountStrategy implements JOOQCountStrategy {

    @Override
    public ItemCount count(DSLContext dslContext, Select<?> query) {
        return ItemCount.exact(dslContext.fetchCount(query));
    }
}
