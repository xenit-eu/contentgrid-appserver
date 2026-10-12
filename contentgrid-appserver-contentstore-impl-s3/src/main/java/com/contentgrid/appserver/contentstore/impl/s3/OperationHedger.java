package com.contentgrid.appserver.contentstore.impl.s3;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.NonNull;

public interface OperationHedger {

    /**
     * Perform request hedging
     * <p>
     * Hedging is sending multiple requests in parallel and picking the first response that completes.
     *
     *
     * @param attemptFactory Creates a new attempt for every call
     * @param discardResult Called to discard results other than the returned one.
     * @param isRetryable Predicate that determines if a thrown exception is retryable, or should be regarded as the final error
     * @return The first completed response
     * @param <T> The type of the response
     * @throws ExecutionException
     * @throws InterruptedException
     * @throws TimeoutException
     */
    <T> T hedge(
            @NonNull Supplier<? extends CompletableFuture<T>> attemptFactory,
            @NonNull Consumer<? super T> discardResult,
            @NonNull Predicate<? super Throwable> isRetryable
    ) throws ExecutionException, InterruptedException, TimeoutException;
}
