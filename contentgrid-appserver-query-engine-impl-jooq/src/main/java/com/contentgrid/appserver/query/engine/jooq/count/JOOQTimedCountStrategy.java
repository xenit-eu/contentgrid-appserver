package com.contentgrid.appserver.query.engine.jooq.count;

import com.contentgrid.appserver.domain.values.ItemCount;
import org.jooq.DSLContext;
import org.jooq.Select;
import org.jooq.impl.DSL;
import org.springframework.dao.QueryTimeoutException;

/**
 * A {@link JOOQCountStrategy} that performs an exact count, and returns an estimate count if it takes longer than 500 ms.
 */
public class JOOQTimedCountStrategy implements JOOQCountStrategy {

    private static final String SAVEPOINT = "count_savepoint";

    private final JOOQCountStrategy exactCountStrategy = new JOOQExactCountStrategy();
    private final JOOQCountStrategy estimateCountStrategy = new JOOQExplainEstimateCountStrategy();

    @Override
    public ItemCount count(DSLContext dslContext, Select<?> query) {
        dslContext.savepoint(SAVEPOINT).execute();
        dslContext.setLocal("statement_timeout", DSL.value(500)).execute();

        ItemCount result;
        try {
            // perform exact count and rollback to savepoint (to reset statement_timeout)
            result = exactCountStrategy.count(dslContext, query);
            dslContext.rollback().toSavepoint(SAVEPOINT).execute();
        } catch (QueryTimeoutException e) {
            // rollback to savepoint first, otherwise we have transaction marked for rollback error
            dslContext.rollback().toSavepoint(SAVEPOINT).execute();
            result = estimateCountStrategy.count(dslContext, query);
        }
        return result;
    }
}
