package com.contentgrid.appserver.query.engine.jooq.test.concurrency;

import org.jooq.ExecuteContext;

public interface ConcurrencyInterferenceExecutor {
    ConcurrencyInterferenceExecutor NOOP = new ConcurrencyInterferenceExecutor() {
        @Override
        public void onQueryStart(ExecuteContext ctx) {

        }

        @Override
        public void onDiscard() {

        }
    };

    void onQueryStart(ExecuteContext ctx);

    void onDiscard();
}
