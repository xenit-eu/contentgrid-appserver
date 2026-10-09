package com.contentgrid.appserver.contentstore.impl.s3;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future.State;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * A simple implementation of hedging.
 * <p>
 * The hedger sends an initial request.
 * If there is no response before {@link HedgingSettings#attemptDelay()}
 */
@RequiredArgsConstructor
public class SimpleOperationHedger implements OperationHedger {
    @NonNull
    private final HedgingSettings settings;

    public interface HedgingSettings {

        /**
         * @return Time to wait for an attempt to complete before starting an additional attempt
         */
        Duration attemptDelay();

        /**
         * @return Total time to wait for the operation to complete, across all attempts
         */
        Duration operationTimeout();

        /**
         * @return Maximum number of attempts that are started
         */
        int maxAttempts();
    }

    @Override
    public <T> T hedge(
            @NonNull Supplier<? extends CompletableFuture<T>> attemptFactory,
            @NonNull Consumer<? super T> discardResult,
            @NonNull Predicate<? super Throwable> isRetryable
    ) throws ExecutionException, InterruptedException, TimeoutException {
        var deadline = System.nanoTime() + settings.operationTimeout().toNanos();

        var attempts = new Attempts<>(attemptFactory);

        try {
            // Start initial attempt
            attempts.startNewAttempt();
            var nextHedge = System.nanoTime() + settings.attemptDelay().toNanos();

            while(true) {
                var hasAttemptsLeft = attempts.getPerformedAttempts() < settings.maxAttempts();
                var completionDeadline = hasAttemptsLeft?Math.min(nextHedge, deadline):deadline;
                var hasCompletion = attempts.awaitAnyCompletion(completionDeadline);

                if (hasCompletion) {
                    try {
                        return attempts.getResult();
                    } catch (ExecutionException executionException) {
                        // This is not a timeout exception, so throw it
                        if(!isRetryable.test(executionException.getCause())) {
                            throw executionException;
                        }
                        // This *is* a timeout exception
                        // 1. reject the current result
                        // 2. If we still have active attempts or we have attempts left, we can continue.
                        //    Else, we have to throw the exception because we don't have any more attempts to do
                        if (!attempts.rejectResult() && !hasAttemptsLeft) {
                            throw executionException;
                        }
                    }
                }

                if (System.nanoTime() < deadline) {
                    // We still have time to perform a new attempt
                    if (hasAttemptsLeft) {
                        attempts.startNewAttempt();
                        nextHedge = System.nanoTime() + settings.attemptDelay().toNanos();
                    }
                } else {
                    // We are completely out of time to finish anything.
                    // Throw and let the finally block cancel all the futures
                    throw new TimeoutException();
                }
            }
        } finally {
            attempts.cancelOthers(discardResult);
        }

    }

    @RequiredArgsConstructor
    private static class Attempts<T> {
        private final Supplier<? extends CompletableFuture<T>> attemptFactory;
        private final Set<CompletableFuture<T>> futures = new HashSet<>();
        private final Set<CompletableFuture<T>> rejectedResults = new HashSet<>();
        private CompletableFuture<T> firstCompleted;

        int getPerformedAttempts() {
            return futures.size() + rejectedResults.size();
        }

        void startNewAttempt() {
            futures.add(attemptFactory.get());
        }

        boolean awaitAnyCompletion(long completionDeadlineNanos) throws InterruptedException {
            try {
                if(futures.isEmpty()) {
                    throw new IllegalStateException("Can not wait when there are no results");
                }
                CompletableFuture.anyOf(futures.toArray(CompletableFuture[]::new))
                        .get(completionDeadlineNanos  - System.nanoTime(), TimeUnit.NANOSECONDS);
            } catch (TimeoutException ex) {
                // There was no actual completion, but a timeout
                return false;
            } catch (ExecutionException ex) {
                // Nothing to do here, the future failed, so this is also a valid completion result
            }
            return true;
        }

        T getResult() throws ExecutionException, InterruptedException {
            if (firstCompleted == null) {
                // First try to use the successful one as completed result
                for (var future : futures) {
                    if (future.state() == State.SUCCESS) {
                        firstCompleted = future;
                        break;
                    }
                }
            }

            if (firstCompleted == null) {
                // Then try to get any future that is done; even if it is a failure
                for (var future : futures) {
                    if (future.isDone()) {
                        firstCompleted = future;
                        break;
                    }
                }
            }
            if (firstCompleted == null) {
                throw new IllegalStateException("Can not retrieve result when there are no completed futures");
            }
            // Future#get() will return (or throw) immediately, because this future is already done
            return firstCompleted.get();
        }

        public void cancelOthers(Consumer<? super T> discardResult) {
            for (var future : futures) {
                if (future == firstCompleted) {
                    continue; // Do not cancel the future that provided our result
                }
                future.cancel(true);
                if(future.state() == State.SUCCESS) {
                    try {
                        discardResult.accept(future.resultNow());
                    } catch (RuntimeException e) {
                        // If a discard failed, continue with discarding the other results.
                    }
                }
            }
        }

        public boolean rejectResult() {
            futures.remove(firstCompleted);
            rejectedResults.add(firstCompleted);
            firstCompleted = null;
            return !futures.isEmpty();
        }
    }


}
