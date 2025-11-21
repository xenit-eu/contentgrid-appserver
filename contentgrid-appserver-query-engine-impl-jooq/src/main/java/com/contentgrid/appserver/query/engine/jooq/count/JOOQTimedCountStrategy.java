package com.contentgrid.appserver.query.engine.jooq.count;

import com.contentgrid.appserver.domain.values.ItemCount;
import com.contentgrid.appserver.query.engine.jooq.PostgresqlErrorType;
import java.time.Duration;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Select;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

/**
 * A {@link JOOQCountStrategy} that performs an exact count, and returns an estimate count
 * if it takes longer than a specified timeout.
 */
@RequiredArgsConstructor
public class JOOQTimedCountStrategy implements JOOQCountStrategy {

    private static final String SAVEPOINT = "count_savepoint";

    private final JOOQCountStrategy exactCountStrategy = new JOOQExactCountStrategy();
    private final JOOQCountStrategy estimateCountStrategy = new JOOQExplainEstimateCountStrategy();

    @NonNull
    private final Duration timeout;

    @Override
    public ItemCount count(DSLContext dslContext, Select<?> query) {
        dslContext.savepoint(SAVEPOINT).execute();
        dslContext.setLocal("statement_timeout", DSL.value(timeout.toMillis())).execute();

        ItemCount result;
        try {
            // perform exact count and rollback to savepoint (to reset statement_timeout)
            result = exactCountStrategy.count(dslContext, query);
            dslContext.rollback().toSavepoint(SAVEPOINT).execute();
        } catch (DataAccessException e) {
            if(PostgresqlErrorType.from(e).is(PostgresqlErrorType.QUERY_TIMEOUT)) {
                // rollback to savepoint first, otherwise we have transaction marked for rollback error
                dslContext.rollback().toSavepoint(SAVEPOINT).execute();
                result = estimateCountStrategy.count(dslContext, query);
            } else {
                throw e;
            }
        }
        return result;
    }
}
