package com.contentgrid.appserver.query.engine.jooq.test.concurrency;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;
import org.jooq.ExecuteContext;
import org.jooq.impl.DSL;

public class CountingConcurrencyInterferenceExecutor implements ConcurrencyInterferenceExecutor{
    private final AtomicInteger count = new AtomicInteger(0);
    @Getter
    private final List<String> queries = new CopyOnWriteArrayList<>();

    public int getCount() {
        return count.get();
    }

    @Override
    public void onQueryStart(ExecuteContext ctx) {
        count.incrementAndGet();
        queries.add(DSL.using(ctx.configuration()).renderInlined(ctx.query()));
    }

    @Override
    public void onDiscard() {
        // Nothing to discard
    }
}
