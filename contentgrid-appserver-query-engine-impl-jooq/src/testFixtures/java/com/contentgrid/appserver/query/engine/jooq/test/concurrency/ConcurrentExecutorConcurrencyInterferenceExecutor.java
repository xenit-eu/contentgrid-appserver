package com.contentgrid.appserver.query.engine.jooq.test.concurrency;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.StandardException;
import lombok.extern.slf4j.Slf4j;
import org.jooq.ExecuteContext;

@Slf4j
public class ConcurrentExecutorConcurrencyInterferenceExecutor implements ConcurrencyInterferenceExecutor{
    private final Thread thread;
    private final int position;
    private final AtomicInteger currentPosition = new AtomicInteger();
    private final AtomicReference<QueryLocation> queryLocation = new AtomicReference<>();
    private final AtomicReference<Throwable> uncaughtException = new AtomicReference<>();

    private static final Duration MIN_LOCK_WAIT = Duration.ofSeconds(1);

    public ConcurrentExecutorConcurrencyInterferenceExecutor(
            @NonNull Runnable execution,
            int position
    ) {
        thread = new Thread(() -> {
            log.info("Interference execution here");
            try {
                execution.run();
            } finally {
                log.info("Interference was finished here");
            }
        });
        thread.setName("Interference worker %d".formatted(position));
        thread.setUncaughtExceptionHandler((th, ex) -> {
            log.error("Exception during interference", ex);
            uncaughtException.set(ex);
        });
        this.position = position;
    }

    public QueryLocation getQueryLocation() {
        return queryLocation.get();
    }

    @Override
    @SneakyThrows(InterruptedException.class)
    public void onQueryStart(ExecuteContext ctx) {
        if (Thread.currentThread() == thread) {
            // We are in our interference thread, don't run our code here
            return;
        }
        var pos = currentPosition.getAndIncrement();
        log.debug("Query #{}: {}", pos, ctx.query());
        if(pos != this.position) {
            // Not in the current position; nothing to do
            return;
        }
        queryLocation.set(new QueryLocation(thread.getName(), ctx.sql()));

        thread.start();
        // should be long enough for everything to finish in case postgres blocks our transaction on the transaction that's under test
        if (thread.join(MIN_LOCK_WAIT)) {
            // Our thread has finished executing; check the thread exception
            checkThreadException();
        } else {
            // The thread did not finish executing, but we'll try to get it again in onDiscard
            log.warn("Interference is blocked for {}; assuming deadlock", MIN_LOCK_WAIT);
        }

    }

    @Override
    @SneakyThrows(InterruptedException.class)
    public void onDiscard() {
        if(queryLocation.get() == null) {
            throw new IllegalStateException("Did not reach expected query position %d (got only %d queries)".formatted(position, currentPosition.get()));
        }
        // Here, we have to wait until the thread finally joins
        thread.join();
        checkThreadException();
    }

    private void checkThreadException() {
        var threadException = this.uncaughtException.getAndSet(null);
        if(threadException != null) {
            throw new InterferenceThreadError(thread.getName(), threadException);
        }
    }


    /**
     * Thrown when there is an uncaught exception thrown by the interference thread.
     * <p>
     * This extends {@link Error} instead of {@link Exception}, because it should not be caught by user code where this throw is interleaved in
     */
    @StandardException
    private static class InterferenceThreadError extends Error {

    }

    /**
     * This is not an exception that is thrown directly.
     * This extends {@linkplain Exception} only because we want a stacktrace of the place where this object is constructed.
     * This object is also added to suppressed exceptions when there is a verification error, to make it easier to pinpoint where in code the
     * query was executed.
     */
    @RequiredArgsConstructor
    @Getter
    public static class QueryLocation extends Exception {
        private final String threadName;
        private final String query;

        @Override
        public String getMessage() {
            return "%s: query '%s'".formatted(threadName, query);
        }
    }
}
