package com.contentgrid.appserver.query.engine.jooq.count;

import com.contentgrid.appserver.domain.values.ItemCount;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Select;
import org.springframework.dao.QueryTimeoutException;

/**
 * A {@link JOOQCountStrategy} that performs an exact count.
 */
public class JOOQExactCountStrategy implements JOOQCountStrategy {

    @Override
    public Optional<ItemCount> count(DSLContext dslContext, Select<?> query) {
        try {
            var exactCount = dslContext.fetchCount(query);
            return Optional.of(ItemCount.exact(exactCount));
        } catch (QueryTimeoutException e) {
            return Optional.empty();
        }
    }
}
