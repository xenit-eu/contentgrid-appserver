package com.contentgrid.appserver.query.engine.jooq.test.concurrency;

import com.contentgrid.appserver.query.engine.jooq.test.concurrency.ConcurrentExecutorConcurrencyInterferenceExecutor.QueryLocation;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.error.MultipleAssertionsError;
import org.jooq.ExecuteContext;
import org.jooq.ExecuteListener;
import org.jooq.ExecuteListenerProvider;

@Slf4j
public class ConcurrencyInterferenceExecuteListenerProvider implements ExecuteListenerProvider {
    private AtomicReference<ConcurrencyInterferenceExecutor> executorRef = new AtomicReference<>(ConcurrencyInterferenceExecutor.NOOP);

    @SneakyThrows
    public void setExecutor(@NonNull ConcurrencyInterferenceExecutor executor) {
        var oldExecutor = executorRef.getAndSet(executor);
        oldExecutor.onDiscard();
    }

    @Override
    public ExecuteListener provide() {
        return new ExecuteListener() {
            @Override
            public void executeStart(ExecuteContext ctx) {
                executorRef.get().onQueryStart(ctx);
            }
        };
    }

    private <P, T> void runUnderExecutor(UnderTestRunnable<P, T> underTestRunnable, ConcurrencyInterferenceExecutor executor, String testName) {
        log.info("{}: prepare", testName);
        var preparation = underTestRunnable.prepare();
        setExecutor(executor);
        T testResult;
        try {
            log.info("{}: test start", testName);
            testResult = underTestRunnable.test(preparation);
            log.info("{}: test end", testName);
        } finally {
            setExecutor(ConcurrencyInterferenceExecutor.NOOP);
        }
        try {
            log.info("{}: verification", testName);
            underTestRunnable.verify(testResult);
        } catch (AssertionError assertionError) {
            if(executor instanceof ConcurrentExecutorConcurrencyInterferenceExecutor hasQueryLocation) {
                assertionError.addSuppressed(hasQueryLocation.getQueryLocation());
            }
            throw assertionError;
        } finally {
            log.info("{}: cleanup", testName);
            underTestRunnable.cleanup();
            log.info("{}: finished", testName);

        }
    }

    public <P, T> void runConcurrencyTest(UnderTestRunnable<P, T> underTestRunnable, Runnable interference) {
        var counter = new CountingConcurrencyInterferenceExecutor();
        runUnderExecutor(underTestRunnable, counter, "Pre-run");

        var count = counter.getCount();
        log.info("Will run {} tests (one before each query)", count);
        log.info("Collected queries: \n{}", counter.getQueries().stream()
                .map(q -> " ** "+q)
                .collect(Collectors.joining("\n")));

        List<AssertionError> assertionErrors = new ArrayList<>(count);

        for(int i = 0; i < count; i++) {
            log.debug("Inject right before query#{} '{}'", i, counter.getQueries().get(i));
            var concurrent = new ConcurrentExecutorConcurrencyInterferenceExecutor(interference, i);
            try {
                runUnderExecutor(underTestRunnable, concurrent, "Test#" + i);
            } catch(AssertionError assertionError) {
                assertionErrors.add(assertionError);
            }
        }

        switch (assertionErrors.size()) {
            case 0 -> { /* do nothing */ }
            case 1 -> throw assertionErrors.getFirst();
            default -> throw new MultipleAssertionsError(assertionErrors);
        }
    }

}
