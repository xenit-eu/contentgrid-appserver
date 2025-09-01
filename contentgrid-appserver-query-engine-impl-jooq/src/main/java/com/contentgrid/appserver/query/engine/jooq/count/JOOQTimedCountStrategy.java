package com.contentgrid.appserver.query.engine.jooq.count;

import com.contentgrid.appserver.domain.values.ItemCount;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Select;
import org.jooq.impl.DSL;

/**
 * A {@link JOOQCountStrategy} that performs an exact count, and returns an estimate count if it takes longer than 500 ms.
 */
public class JOOQTimedCountStrategy implements JOOQCountStrategy {

    private final JOOQCountStrategy exactCountStrategy = new JOOQExactCountStrategy();
    private final JOOQCountStrategy estimateCountStrategy = new JOOQExplainEstimateCountStrategy();

    @Override
    public Optional<ItemCount> count(DSLContext dslContext, Select<?> query) {
        dslContext.savepoint("savepoint").execute();
        dslContext.setLocal("statement_timeout", DSL.value(500)).execute();
        var result = exactCountStrategy.count(dslContext, query);
        dslContext.rollback().toSavepoint("savepoint").execute();
        return result.or(() -> estimateCountStrategy.count(dslContext, query));
    }
}
