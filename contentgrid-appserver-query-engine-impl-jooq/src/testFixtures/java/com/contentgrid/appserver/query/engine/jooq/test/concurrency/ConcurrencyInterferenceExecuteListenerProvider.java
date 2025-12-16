package com.contentgrid.appserver.query.engine.jooq.test.concurrency;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.error.MultipleAssertionsError;
import org.jooq.ExecuteContext;
import org.jooq.ExecuteListener;
import org.jooq.ExecuteListenerProvider;

/**
 * Entrypoint for concurrency testing.
 * <p>
 * This listener is registered with jOOQ, after which concurrency tests can be executed using {@link #runConcurrencyTest(UnderTestRunnable, Runnable)}
 */
@Slf4j
public class ConcurrencyInterferenceExecuteListenerProvider implements ExecuteListenerProvider {
    private final AtomicReference<ConcurrencyInterferenceExecutor> executorRef = new AtomicReference<>(ConcurrencyInterferenceExecutor.NOOP);

    private interface CloseableExecutorRef extends AutoCloseable {

        @Override
        void close();
    }

    @SneakyThrows
    private CloseableExecutorRef setExecutor(@NonNull ConcurrencyInterferenceExecutor executor) {
        var oldExecutor = executorRef.getAndSet(executor);
        oldExecutor.onDiscard();
        return () -> setExecutor(ConcurrencyInterferenceExecutor.NOOP);
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

    private <P, T> void runUnderExecutor(UnderTestRunnable<P, T> underTestRunnable, Function<P, ConcurrencyInterferenceExecutor> executorFactory, String testName) {
        log.info("{}: prepare", testName);
        var preparation = underTestRunnable.prepare();
        var executor = executorFactory.apply(preparation);
        T testResult;
        try(CloseableExecutorRef ignored = setExecutor(executor)) {
            log.info("{}: test start", testName);
            testResult = underTestRunnable.test(preparation);
            log.info("{}: test end", testName);
        }
        try {
            log.info("{}: verification", testName);
            underTestRunnable.verify(preparation, testResult);
        } catch (AssertionError assertionError) {
            if(executor instanceof ConcurrentExecutorConcurrencyInterferenceExecutor hasQueryLocation) {
                assertionError.addSuppressed(hasQueryLocation.getQueryLocation());
            }
            throw assertionError;
        } finally {
            log.info("{}: cleanup", testName);
            underTestRunnable.cleanup(preparation, testResult);
            log.info("{}: finished", testName);

        }
    }

    public <P, T> void runConcurrencyTest(UnderTestRunnable<P, T> underTestRunnable, Runnable interference) {
        runConcurrencyTest(underTestRunnable, p -> interference.run());
    }

    public <P, T> void runConcurrencyTest(UnderTestRunnable<P, T> underTestRunnable, Consumer<P> interference) {
        var counter = new CountingConcurrencyInterferenceExecutor();
        runUnderExecutor(underTestRunnable, p -> {
            // Run interference early, so verification can also look at the effects of the interference code for asserts
            interference.accept(p);
            return counter;
        }, "Pre-run");

        var count = counter.getCount();
        log.info("Will run {} tests (one before each query)", count);
        log.info("Collected queries: \n{}", counter.getQueries().stream()
                .map(q -> " ** "+q)
                .collect(Collectors.joining("\n")));

        List<AssertionError> assertionErrors = new ArrayList<>(count);

        for(int i = 0; i < count; i++) {
            log.debug("Inject right before query#{} '{}'", i, counter.getQueries().get(i));
            var queryPos = i;
            try {
                runUnderExecutor(underTestRunnable, p -> new ConcurrentExecutorConcurrencyInterferenceExecutor(() -> {
                    interference.accept(p);
                }, queryPos), "Test#" + i);
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
