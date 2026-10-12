package com.contentgrid.appserver.contentstore.impl.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contentgrid.appserver.contentstore.impl.s3.SimpleOperationHedger.HedgingSettings;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class SimpleOperationHedgerTest {

    private record Settings(Duration attemptDelay, Duration operationTimeout, int maxAttempts)
            implements HedgingSettings {

    }

    private static final Settings SETTINGS = new Settings(Duration.ofMillis(50), Duration.ofSeconds(2), 3);

    /**
     * Hands out pre-made futures, one for every attempt
     */
    private static class Attempts implements Supplier<CompletableFuture<String>> {
        final List<CompletableFuture<String>> futures;
        final List<CompletableFuture<String>> started = new CopyOnWriteArrayList<>();

        Attempts(int count) {
            futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                futures.add(new CompletableFuture<>());
            }
        }

        @Override
        public CompletableFuture<String> get() {
            var future = futures.get(started.size());
            started.add(future);
            return future;
        }
    }

    private final List<String> discarded = new CopyOnWriteArrayList<>();
    private final OperationHedger hedger = new SimpleOperationHedger(SETTINGS);

    @Test
    void fastFirstAttempt_noHedges() throws Exception {
        var attempts = new Attempts(3);
        attempts.futures.getFirst().complete("first");

        assertThat(hedger.hedge(attempts, discarded::add, TimeoutException.class::isInstance)).isEqualTo("first");
        assertThat(attempts.started).hasSize(1);
        assertThat(discarded).isEmpty();
    }

    @Test
    void slowFirstAttempt_hedgeWins() throws Exception {
        var attempts = new Attempts(3);
        attempts.futures.get(1).complete("second");

        assertThat(hedger.hedge(attempts, discarded::add, TimeoutException.class::isInstance)).isEqualTo("second");
        assertThat(attempts.started).hasSize(2);
        assertThat(attempts.futures.getFirst()).isCancelled();
        assertThat(discarded).isEmpty();
    }

    @Test
    void neverStartsMoreThanMaxAttempts() {
        var attempts = new Attempts(10);
        var hedger = new SimpleOperationHedger(new Settings(Duration.ofMillis(10), Duration.ofMillis(300), 3));

        assertThatThrownBy(() -> hedger.hedge(attempts, discarded::add, TimeoutException.class::isInstance)).isInstanceOf(TimeoutException.class);
        assertThat(attempts.started).hasSize(3).allSatisfy(f -> assertThat(f).isCancelled());
    }

    @Test
    void timedOutAttempt_immediatelyStartsHedge() throws Exception {
        var attempts = new Attempts(3);
        // Attempt offset is longer than the operation timeout, so only failures can trigger a new attempt
        var hedger = new SimpleOperationHedger(new Settings(Duration.ofHours(1), Duration.ofSeconds(2), 3));
        attempts.futures.get(0).completeExceptionally(new TimeoutException("first"));
        attempts.futures.get(1).complete("second");

        assertThat(hedger.hedge(attempts, discarded::add, TimeoutException.class::isInstance)).isEqualTo("second");
        assertThat(attempts.started).hasSize(2);
    }

    @Test
    void timedOutAttempt_doesNotFailOperationWhileOthersInFlight() throws Exception {
        var attempts = new Attempts(3);
        var hedger = new SimpleOperationHedger(new Settings(Duration.ofMillis(50), Duration.ofSeconds(2), 2));
        // Second attempt times out, the first one is still running and completes later
        attempts.futures.get(1).completeExceptionally(new TimeoutException("second"));
        CompletableFuture.delayedExecutor(200, TimeUnit.MILLISECONDS)
                .execute(() -> attempts.futures.getFirst().complete("first"));

        assertThat(hedger.hedge(attempts, discarded::add, TimeoutException.class::isInstance)).isEqualTo("first");
        assertThat(attempts.started).hasSize(2);
    }

    @Test
    void failedAttempt_failsOperationImmediately() {
        var attempts = new Attempts(3);
        var failure = new IllegalStateException("first");
        attempts.futures.get(0).completeExceptionally(failure);
        attempts.futures.get(1).complete("second");

        assertThatThrownBy(() -> hedger.hedge(attempts, discarded::add, TimeoutException.class::isInstance))
                .isInstanceOf(ExecutionException.class)
                .hasCause(failure);
        assertThat(attempts.started).hasSize(1);
    }

    @Test
    void failedHedge_failsOperationAndCancelsOthers() {
        var attempts = new Attempts(3);
        var failure = new IllegalStateException("second");
        attempts.futures.get(1).completeExceptionally(failure);

        assertThatThrownBy(() -> hedger.hedge(attempts, discarded::add, TimeoutException.class::isInstance))
                .isInstanceOf(ExecutionException.class)
                .hasCause(failure);
        assertThat(attempts.started).hasSize(2);
        assertThat(attempts.futures.getFirst()).isCancelled();
    }

    @Test
    void allAttemptsTimeOut() {
        var attempts = new Attempts(3);
        var first = new TimeoutException("first");
        var second = new TimeoutException("second");
        var third = new TimeoutException("third");
        attempts.futures.get(0).completeExceptionally(first);
        attempts.futures.get(1).completeExceptionally(second);
        attempts.futures.get(2).completeExceptionally(third);

        assertThatThrownBy(() -> hedger.hedge(attempts, discarded::add, TimeoutException.class::isInstance))
                .isInstanceOf(ExecutionException.class)
                .hasCause(third);
    }

    @Test
    void lateSuccessfulResults_areDiscarded() throws Exception {
        var attempts = new Attempts(3) {
            @Override
            public CompletableFuture<String> get() {
                var future = super.get();
                if (started.size() == 2) {
                    // Both attempts complete at the same time; only one of them can win
                    started.get(0).complete("first");
                    started.get(1).complete("second");
                }
                return future;
            }
        };

        var result = hedger.hedge(attempts, discarded::add, TimeoutException.class::isInstance);

        assertThat(attempts.started).hasSize(2);
        assertThat(result).isIn("first", "second");
        assertThat(discarded).containsExactly(result.equals("first") ? "second" : "first");
    }

    @Test
    void interrupted_cancelsAllAttempts() throws Exception {
        var attempts = new Attempts(3);
        var thread = Thread.currentThread();
        CompletableFuture.delayedExecutor(80, TimeUnit.MILLISECONDS).execute(thread::interrupt);

        assertThatThrownBy(() -> hedger.hedge(attempts, discarded::add, TimeoutException.class::isInstance)).isInstanceOf(InterruptedException.class);
        assertThat(attempts.started).isNotEmpty().allSatisfy(f -> assertThat(f).isCancelled());
    }
}
