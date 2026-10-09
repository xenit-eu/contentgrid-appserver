package com.contentgrid.appserver.contentstore.impl.s3;

import com.contentgrid.appserver.contentstore.api.ContentAccessor;
import com.contentgrid.appserver.contentstore.api.ContentReader;
import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.contentstore.api.UnreadableContentException;
import com.contentgrid.appserver.contentstore.api.UnwritableContentException;
import com.contentgrid.appserver.contentstore.api.range.ResolvedContentRange;
import com.contentgrid.appserver.contentstore.impl.utils.GuardedContentReader;
import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * {@link ContentStore} backed by S3.
 * <p>
 * Read acquisition (everything before {@link #getReader} returns its streaming reader) retries
 * narrowly typed transient connection-establishment failures, bounded by {@link S3ReadRetryPolicy}:
 * at most {@code maxRetries} additional {@code getObject} operations within a shared monotonic
 * acquisition deadline. Only the acquisition phase is bounded and retried: once the response stream
 * is handed to the caller, large or slow downloads continue outside this acquisition deadline.
 * Validation failures and failures during stream consumption are never retried.
 */
@Slf4j
public class S3ContentStore implements ContentStore {

    @NonNull
    private final S3AsyncClient client;

    @NonNull
    private final String bucketName;

    @NonNull
    private final S3ReadRetryPolicy readRetryPolicy;

    private static final String CONTENT_TYPE = "application/octet-stream";

    private static final String ACQUISITION_DEADLINE_EXCEEDED =
            "S3 content read acquisition deadline exceeded after {} attempts in {} ms";

    public S3ContentStore(@NonNull S3AsyncClient client, @NonNull String bucketName) {
        this(client, bucketName, S3ReadRetryPolicy.DEFAULT);
    }

    public S3ContentStore(@NonNull S3AsyncClient client, @NonNull String bucketName,
            @NonNull S3ReadRetryPolicy readRetryPolicy) {
        this.client = client;
        this.bucketName = bucketName;
        this.readRetryPolicy = readRetryPolicy;
    }

    @Override
    public ContentReader getReader(@NonNull ContentReference contentReference, ResolvedContentRange contentRange)
            throws UnreadableContentException {
        var requestBuilder = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(contentReference.getValue());
        if (contentRange != null) {
            requestBuilder.range("bytes=%d-%d".formatted(contentRange.getStartByte(),
                    contentRange.getEndByteInclusive()));
        }
        var getObjectRequest = requestBuilder.build();

        var startedNanos = System.nanoTime();
        var deadlineNanos = startedNanos + readRetryPolicy.acquisitionTimeout().toNanos();
        var attempts = 0;

        while (true) {
            checkAcquisitionAllowed(contentReference, deadlineNanos, startedNanos, attempts);
            attempts++;
            try {
                var object = acquireAttempt(getObjectRequest, deadlineNanos);
                return createReader(contentReference, contentRange, object, attempts, startedNanos);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw unreadable(contentReference, e);
            } catch (CancellationException e) {
                throw unreadable(contentReference, e);
            } catch (ExecutionException e) {
                var failure = Objects.requireNonNullElse(e.getCause(), e);
                if (!shouldRetry(failure, attempts, startedNanos)) {
                    throw unreadable(contentReference, failure);
                }
                sleepBeforeRetry(contentReference, deadlineNanos, startedNanos, attempts);
            } catch (TimeoutException e) {
                log.warn(ACQUISITION_DEADLINE_EXCEEDED, attempts, elapsedMillis(startedNanos));
                throw unreadable(contentReference, e);
            }
        }
    }

    private static void checkAcquisitionAllowed(ContentReference reference, long deadlineNanos, long startedNanos,
            int attempts) throws UnreadableContentException {
        if (Thread.currentThread().isInterrupted()) {
            throw unreadable(reference, new InterruptedException("S3 read acquisition interrupted"));
        }
        if (System.nanoTime() >= deadlineNanos) {
            log.warn(ACQUISITION_DEADLINE_EXCEEDED, attempts, elapsedMillis(startedNanos));
            throw unreadable(reference, new TimeoutException("S3 read acquisition deadline exceeded"));
        }
    }

    private ResponseInputStream<GetObjectResponse> acquireAttempt(GetObjectRequest request, long deadlineNanos)
            throws InterruptedException, ExecutionException, TimeoutException {
        // A fresh SDK operation and response transformer per attempt; reuse the immutable request.
        CompletableFuture<ResponseInputStream<GetObjectResponse>> operation;
        try {
            operation = client.getObject(request, AsyncResponseTransformer.toBlockingInputStream());
        } catch (RuntimeException issueFailure) {
            operation = CompletableFuture.failedFuture(issueFailure);
        }

        // Abort unowned streams arriving after abandonment, including non-propagating cancellation.
        var abandoned = new AtomicBoolean(false);
        operation.whenComplete((stream, failure) -> {
            if (stream != null && abandoned.get()) {
                abortQuietly(stream);
            }
        });

        try {
            return operation.get(deadlineNanos - System.nanoTime(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException | TimeoutException e) {
            abandon(operation, abandoned);
            throw e;
        }
    }

    private boolean shouldRetry(Throwable failure, int attempts, long startedNanos) {
        if (!S3ConnectFailureClassifier.isRetryableConnectFailure(failure)) {
            return false;
        }
        if (attempts > readRetryPolicy.maxRetries()) {
            log.warn("S3 content read acquisition exhausted after {} attempts in {} ms, last failure: {}",
                    attempts, elapsedMillis(startedNanos),
                    S3ConnectFailureClassifier.connectFailureCategory(failure));
            return false;
        }
        log.info("Retrying S3 content read acquisition: attempt {} of {} failed with {} after {} ms",
                attempts, readRetryPolicy.maxRetries() + 1,
                S3ConnectFailureClassifier.connectFailureCategory(failure), elapsedMillis(startedNanos));
        return true;
    }

    private static ContentReader createReader(ContentReference reference, ResolvedContentRange range,
            ResponseInputStream<GetObjectResponse> object, int attempts, long startedNanos)
            throws UnreadableContentException {
        try {
            if (range != null && contentSize(object.response()) != range.getContentSize()) {
                abortQuietly(object);
                throw new UnreadableContentException(reference, "range size does not match actual size");
            }
            if (attempts > 1) {
                log.info("S3 content read acquisition recovered after {} attempts in {} ms",
                        attempts, elapsedMillis(startedNanos));
            }
            return new GuardedContentReader(new S3ContentReader(reference, object));
        } catch (RuntimeException e) {
            // Until handoff we own the stream, including when response metadata is malformed.
            // Cleanup must not mask the validation failure or turn it into a retry.
            abortQuietly(object);
            throw unreadable(reference, e);
        }
    }

    /**
     * Marks the attempt abandoned and cancels its operation. A stream that still wins the race against
     * cancellation is reclaimed synchronously; streams arriving later are aborted by the handler
     * registered when the operation was issued. A cancelled or failed future has no usable stream;
     * best-effort reclamation must not replace the acquisition failure already propagated by the caller.
     */
    @SuppressWarnings("java:S1166")
    private static void abandon(CompletableFuture<ResponseInputStream<GetObjectResponse>> operation,
            AtomicBoolean abandoned) {
        abandoned.set(true);
        operation.cancel(true);
        try {
            // getNow reports a cancelled or failed operation by throwing: only a normally completed
            // stream is reclaimed here, later arrivals are aborted by the handler registered at issue time
            var lateStream = operation.getNow(null);
            if (lateStream != null) {
                abortQuietly(lateStream);
            }
        } catch (RuntimeException ignored) {
            // cancelled or failed: there is no usable stream to reclaim
        }
    }

    /**
     * Wraps an acquisition failure, preserving the underlying cause even when it carries no message
     * (the {@link UnreadableContentException} cause constructor rejects null messages).
     */
    private static UnreadableContentException unreadable(ContentReference reference, Throwable cause) {
        var message = cause.getMessage() != null ? cause.getMessage() : cause.toString();
        var failure = new UnreadableContentException(reference, message);
        failure.initCause(cause);
        return failure;
    }

    /** Best-effort cleanup of an unowned stream must never replace the original acquisition failure. */
    @SuppressWarnings("java:S1166")
    private static void abortQuietly(ResponseInputStream<GetObjectResponse> stream) {
        try {
            stream.abort();
        } catch (RuntimeException ignored) {
            // Do not mask the primary failure or log potentially sensitive SDK exception details.
        }
    }

    /**
     * Sleeps a jittered backoff within the shared acquisition deadline. Throws when the remaining
     * budget cannot hold the backoff or when interrupted, restoring the interrupted flag.
     */
    private void sleepBeforeRetry(ContentReference contentReference, long deadlineNanos, long startedNanos,
            int attempts) throws UnreadableContentException {
        var minNanos = readRetryPolicy.minDelay().toNanos();
        var maxNanos = readRetryPolicy.maxDelay().toNanos();
        var delayNanos = minNanos == maxNanos ? minNanos : ThreadLocalRandom.current().nextLong(minNanos, maxNanos);
        if (delayNanos == 0) {
            return;
        }
        if (deadlineNanos - System.nanoTime() < delayNanos) {
            log.warn(ACQUISITION_DEADLINE_EXCEEDED, attempts, elapsedMillis(startedNanos));
            throw unreadable(contentReference,
                    new TimeoutException("S3 read acquisition deadline exceeded"));
        }
        try {
            TimeUnit.NANOSECONDS.sleep(delayNanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unreadable(contentReference, e);
        }
    }

    private static long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private static long contentSize(GetObjectResponse response) {
        var contentRange = response.contentRange();
        if (contentRange != null) {
            return Long.parseLong(contentRange.split("/", 2)[1]);
        }
        return response.contentLength();
    }

    @Override
    public ContentAccessor writeContent(@NonNull InputStream inputStream) throws UnwritableContentException {
        var contentReference = ContentReference.of(UUID.randomUUID().toString());
        try {
            client.putObject(PutObjectRequest.builder()
                                    .bucket(bucketName)
                                    .key(contentReference.getValue())
                                    .contentType(CONTENT_TYPE)
                                    .build(),
                            AsyncRequestBody.fromInputStream(inputStream, null)) // length unknown
                    .join();
            return new S3ContentAccessor(contentReference);
        } catch (CompletionException e) {
            throw new UnwritableContentException(contentReference, e.getCause());
        } catch (RuntimeException e) {
            throw new UnwritableContentException(contentReference, e);
        }
    }

    @Override
    public void remove(@NonNull ContentReference contentReference) throws UnwritableContentException {
        try {
            client.deleteObject(DeleteObjectRequest.builder()
                            .bucket(bucketName)
                            .key(contentReference.getValue())
                            .build())
                    .join();
        } catch (CompletionException e) {
            throw new UnwritableContentException(contentReference, e.getCause());
        } catch (RuntimeException e) {
            throw new UnwritableContentException(contentReference, e);
        }
    }

}
