package com.contentgrid.appserver.contentstore.impl.s3;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Does not perform any hedging at all, only sets a timeout deadline for the operation
 */
@RequiredArgsConstructor
public class NoOperationHedger implements OperationHedger {
    @NonNull
    private final Duration operationTimeout;

    @Override
    public <T> T hedge(@NonNull Supplier<? extends CompletableFuture<T>> attemptFactory,
            @NonNull Consumer<? super T> discardResult, @NonNull Predicate<? super Throwable> isRetryable)
            throws ExecutionException, InterruptedException, TimeoutException {
        return attemptFactory.get().get(operationTimeout.toNanos(), TimeUnit.NANOSECONDS);
    }
}
