package com.contentgrid.appserver.query.engine.jooq.count;

import com.contentgrid.appserver.domain.values.ItemCount;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Select;

/**
 * A {@link JOOQCountStrategy} that performs an estimated count based on the query plan.
 */
public class JOOQExplainEstimateCountStrategy implements JOOQCountStrategy {

    @Override
    public Optional<ItemCount> count(DSLContext dslContext, Select<?> query) {
        var estimatedCount = dslContext.explain(query).rows();

        if (Double.isNaN(estimatedCount)) {
            return Optional.empty();
        }
        return Optional.of(ItemCount.estimated(Math.round(estimatedCount)));
    }
}
