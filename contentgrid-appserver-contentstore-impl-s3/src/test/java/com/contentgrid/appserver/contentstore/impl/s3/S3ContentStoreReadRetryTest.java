package com.contentgrid.appserver.contentstore.impl.s3;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.UnreadableContentException;
import com.contentgrid.appserver.contentstore.api.range.ContentRangeRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import javax.net.ssl.SSLException;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.core.exception.NonRetryableException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Unit tests for the S3 read-acquisition retry behavior of {@link S3ContentStore#getReader}.
 * The {@link S3AsyncClient} is a controllable fixture; every assertion targets real store behavior
 * (attempt counts, request preservation, stream ownership, deadline and interruption handling).
 */
class S3ContentStoreReadRetryTest {

    private static final ContentReference REFERENCE = ContentReference.of("test-object");

    private static final byte[] CONTENT = "hello-content".getBytes();

    // --- fixtures ---

    private static S3AsyncClient mockClient() {
        return mock(S3AsyncClient.class);
    }

    private static Throwable connectFailure() {
        return SdkClientException.create("Unable to connect", new ConnectException("Connection refused"));
    }

    private static Throwable nestedMultipartConnectFailure() {
        return NonRetryableException.create("Error occurred during multipart download. Request will not be retried.",
                SdkClientException.create("Unable to connect", new ConnectException("Connection refused")));
    }

    private static Throwable nestedNettyTimeoutFailure() {
        return NonRetryableException.create("Error occurred during multipart download. Request will not be retried.",
                SdkClientException.create("Unable to connect",
                        new io.netty.channel.ConnectTimeoutException("connection timed out")));
    }

    private static <T> CompletableFuture<T> failed(Throwable failure) {
        return CompletableFuture.failedFuture(failure);
    }

    private static CompletableFuture<ResponseInputStream<GetObjectResponse>> success() {
        return CompletableFuture.completedFuture(stream(GetObjectResponse.builder()
                .contentLength((long) CONTENT.length)
                .build(), CONTENT, new AtomicBoolean()));
    }

    private static ResponseInputStream<GetObjectResponse> stream(GetObjectResponse response, byte[] content,
            AtomicBoolean aborted) {
        return new ResponseInputStream<>(response,
                AbortableInputStream.create(new java.io.ByteArrayInputStream(content.clone()),
                        () -> aborted.set(true)));
    }

    private static void assertCausedByConnect(Throwable throwable) {
        var current = throwable;
        while (current != null && !(current instanceof ConnectException)) {
            current = current.getCause();
        }
        assertTrue(current instanceof ConnectException, "expected a ConnectException cause, got: " + throwable);
    }

    // --- fail-then-success / exhaustion / maxRetries=0 ---

    @Test
    void failThenSuccess_retriesOnceAndReturnsReadableReader() throws Exception {
        var client = mockClient();
        doReturn(failed(connectFailure()), success()).when(client)
                .getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));

        var store = new S3ContentStore(client, "bucket");
        try (var reader = new java.io.BufferedInputStream(
                store.getReader(REFERENCE, null).getContentInputStream())) {
            assertArrayEquals(CONTENT, reader.readAllBytes());
        }

        verify(client, times(2)).getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));
    }

    @Test
    void alwaysFailingRetryable_exhaustsRetriesAndPreservesCause() {
        var client = mockClient();
        doReturn(failed(connectFailure())).when(client)
                .getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));

        var store = new S3ContentStore(client, "bucket");
        var failure = assertThrows(UnreadableContentException.class, () -> store.getReader(REFERENCE, null));

        assertCausedByConnect(failure.getCause());
        // default maxRetries=1: one initial attempt plus one retry
        verify(client, times(2)).getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));
    }

    @Test
    void maxRetriesZero_disablesOuterRetries() {
        var client = mockClient();
        doReturn(failed(connectFailure())).when(client)
                .getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));

        var store = new S3ContentStore(client, "bucket", new S3ReadRetryPolicy(0, Duration.ofSeconds(10)));
        var failure = assertThrows(UnreadableContentException.class, () -> store.getReader(REFERENCE, null));

        assertCausedByConnect(failure.getCause());
        verify(client, times(1)).getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));
    }

    // --- classification: nested timeout causes are retryable ---

    static Stream<Throwable> retryableFailures() {
        return Stream.of(
                connectFailure(),
                new CompletionException(nestedMultipartConnectFailure()),
                nestedMultipartConnectFailure(),
                nestedNettyTimeoutFailure(),
                new ExecutionException(connectFailure())
        );
    }

    @ParameterizedTest
    @MethodSource("retryableFailures")
    void nestedTimeoutCauses_areRetried(Throwable failure) {
        var client = mockClient();
        doReturn(failed(failure)).when(client)
                .getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));

        var store = new S3ContentStore(client, "bucket");
        assertThrows(UnreadableContentException.class, () -> store.getReader(REFERENCE, null));

        verify(client, times(2)).getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));
    }

    // --- classification: permanent failures are not retried ---

    static Stream<Throwable> permanentFailures() {
        return Stream.of(
                S3Exception.builder().message("No such key").statusCode(404).build(),
                S3Exception.builder().message("Access denied").statusCode(403).build(),
                new IOException("Read timed out"),
                new java.net.SocketTimeoutException("Read timed out"),
                new SSLException("TLS handshake failure"),
                new RuntimeException("boom"),
                SdkClientException.create("some other client failure")
        );
    }

    @ParameterizedTest
    @MethodSource("permanentFailures")
    void permanentFailures_areNotRetried(Throwable failure) {
        var client = mockClient();
        doReturn(failed(failure)).when(client)
                .getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));

        var store = new S3ContentStore(client, "bucket");
        var thrown = assertThrows(UnreadableContentException.class, () -> store.getReader(REFERENCE, null));

        assertEquals(failure, thrown.getCause());
        verify(client, times(1)).getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));
    }

    @Test
    void cancellation_isNotRetried() {
        var client = mockClient();
        CompletableFuture<ResponseInputStream<GetObjectResponse>> cancelled = new CompletableFuture<>();
        cancelled.cancel(false);
        doReturn(cancelled).when(client).getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));

        var store = new S3ContentStore(client, "bucket");
        assertThrows(UnreadableContentException.class, () -> store.getReader(REFERENCE, null));

        verify(client, times(1)).getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));
    }

    // --- range preservation / size check ---

    @Test
    void rangedRead_preservesRangeAcrossRetries() throws Exception {
        var range = ContentRangeRequest.createRange(0, 3).resolve(10);
        var client = mockClient();
        var aborted = new AtomicBoolean();
        var response = GetObjectResponse.builder().contentRange("bytes 0-9/10").contentLength(10L).build();
        doReturn(failed(connectFailure()),
                CompletableFuture.completedFuture(stream(response, CONTENT, aborted)))
                .when(client).getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));

        var store = new S3ContentStore(client, "bucket");
        var reader = store.getReader(REFERENCE, range);
        assertTrue(reader.getContentInputStream().readAllBytes().length > 0);

        var captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(client, times(2)).getObject(captor.capture(), any(AsyncResponseTransformer.class));
        assertEquals("bytes=0-3", captor.getAllValues().get(0).range());
        assertEquals("bytes=0-3", captor.getAllValues().get(1).range());
    }

    @Test
    void rangeSizeMismatch_abortsStreamAndDoesNotRetry() throws Exception {
        var range = ContentRangeRequest.createRange(0, 3).resolve(10);
        var client = mockClient();
        var aborted = new AtomicBoolean();
        // total object size 999 disagrees with the requested range's content size 10
        var response = GetObjectResponse.builder().contentRange("bytes 0-3/999").contentLength(4L).build();
        doReturn(CompletableFuture.completedFuture(stream(response, CONTENT, aborted)))
                .when(client).getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));

        var store = new S3ContentStore(client, "bucket");
        assertThrows(UnreadableContentException.class, () -> store.getReader(REFERENCE, range));

        assertTrue(aborted.get(), "mismatched stream must be aborted");
        verify(client, times(1)).getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));
    }

    static Stream<GetObjectResponse> malformedRangeResponses() {
        return Stream.of(
                GetObjectResponse.builder().contentRange("bytes 0-3/not-a-number").contentLength(4L).build(),
                GetObjectResponse.builder().contentRange("bytes 0-3").contentLength(4L).build(),
                GetObjectResponse.builder().build()
        );
    }

    @ParameterizedTest
    @MethodSource("malformedRangeResponses")
    void malformedRangeMetadata_abortsStreamWrapsFailureAndDoesNotRetry(GetObjectResponse response) throws Exception {
        var range = ContentRangeRequest.createRange(0, 3).resolve(10);
        var client = mockClient();
        var aborted = new AtomicBoolean();
        doReturn(CompletableFuture.completedFuture(stream(response, CONTENT, aborted)))
                .when(client).getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));

        var store = new S3ContentStore(client, "bucket");
        var failure = assertThrows(UnreadableContentException.class, () -> store.getReader(REFERENCE, range));

        assertTrue(failure.getCause() instanceof RuntimeException, "metadata failure cause must be retained");
        assertTrue(aborted.get(), "invalid response stream must be aborted");
        verify(client, times(1)).getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));
    }

    @Test
    void malformedRangeMetadata_abortFailureDoesNotMaskMetadataFailure() throws Exception {
        var range = ContentRangeRequest.createRange(0, 3).resolve(10);
        var client = mockClient();
        var aborted = new AtomicBoolean();
        var response = GetObjectResponse.builder().contentRange("bytes 0-3/not-a-number").contentLength(4L).build();
        var object = new ResponseInputStream<>(response, AbortableInputStream.create(
                new java.io.ByteArrayInputStream(CONTENT), () -> {
                    aborted.set(true);
                    throw new IllegalStateException("abort failed");
                }));
        doReturn(CompletableFuture.completedFuture(object))
                .when(client).getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));

        var failure = assertThrows(UnreadableContentException.class,
                () -> new S3ContentStore(client, "bucket").getReader(REFERENCE, range));

        assertTrue(failure.getCause() instanceof NumberFormatException, "original metadata cause must be retained");
        assertTrue(aborted.get(), "abort must be attempted even when it fails");
        verify(client, times(1)).getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));
    }

    @Test
    void rangeSizeMismatch_abortFailureDoesNotMaskRangeFailure() throws Exception {
        var range = ContentRangeRequest.createRange(0, 3).resolve(10);
        var client = mockClient();
        var aborted = new AtomicBoolean();
        var response = GetObjectResponse.builder().contentRange("bytes 0-3/999").contentLength(4L).build();
        var object = new ResponseInputStream<>(response, AbortableInputStream.create(
                new java.io.ByteArrayInputStream(CONTENT), () -> {
                    aborted.set(true);
                    throw new IllegalStateException("abort failed");
                }));
        doReturn(CompletableFuture.completedFuture(object))
                .when(client).getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));

        var failure = assertThrows(UnreadableContentException.class,
                () -> new S3ContentStore(client, "bucket").getReader(REFERENCE, range));

        assertTrue(failure.getMessage().contains("range size does not match actual size"));
        assertTrue(aborted.get(), "abort must be attempted even when it fails");
        verify(client, times(1)).getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));
    }

    // --- shared deadline ---

    @Test
    void hungFuture_exceedsDeadlineCancelsOperation() {
        var hung = new CompletableFuture<ResponseInputStream<GetObjectResponse>>();
        var client = mockClient();
        doReturn(hung).when(client).getObject(any(GetObjectRequest.class),
                any(AsyncResponseTransformer.class));

        var store = new S3ContentStore(client, "bucket",
                new S3ReadRetryPolicy(1, Duration.ofMillis(300)));
        var start = System.nanoTime();
        var thrown = assertThrows(UnreadableContentException.class, () -> store.getReader(REFERENCE, null));
        var elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertTrue(thrown.getCause() instanceof TimeoutException, "cause was " + thrown.getCause());
        assertTrue(elapsed.compareTo(Duration.ofSeconds(5)) < 0, "must time out via deadline, took " + elapsed);
        assertTrue(hung.isCancelled(), "abandoned operation must be cancelled");
        verify(client, times(1)).getObject(any(GetObjectRequest.class),
                any(AsyncResponseTransformer.class));
    }

    @Test
    void lateArrivalAfterAbandonment_isAborted() {
        // An operation future that ignores cancellation: models the worst case where cancelling the
        // outer future did not propagate to the downloader and a stream still arrives afterwards.
        var uncooperative = new CompletableFuture<ResponseInputStream<GetObjectResponse>>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return false;
            }
        };
        var client = mockClient();
        doReturn(uncooperative).when(client).getObject(any(GetObjectRequest.class),
                any(AsyncResponseTransformer.class));

        var store = new S3ContentStore(client, "bucket",
                new S3ReadRetryPolicy(1, Duration.ofMillis(200)));
        assertThrows(UnreadableContentException.class, () -> store.getReader(REFERENCE, null));

        // a stream arriving after abandonment is unowned and must be aborted on arrival
        var lateAborted = new AtomicBoolean();
        uncooperative.complete(stream(GetObjectResponse.builder()
                .contentLength((long) CONTENT.length)
                .build(), CONTENT, lateAborted));
        assertTrue(lateAborted.get(), "late stream must be aborted");
    }

    @Test
    void retryDelay_sharesDeadline_noSecondAttemptWhenDelayDoesNotFit() {
        var client = mockClient();
        doReturn(failed(connectFailure())).when(client)
                .getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));

        // An explicit minimum of 100ms never fits into a 50ms budget.
        var store = new S3ContentStore(client, "bucket",
                new S3ReadRetryPolicy(5, Duration.ofMillis(50), Duration.ofMillis(100), Duration.ofMillis(250)));
        var start = System.nanoTime();
        assertThrows(UnreadableContentException.class, () -> store.getReader(REFERENCE, null));
        var elapsed = Duration.ofNanos(System.nanoTime() - start);

        verify(client, times(1)).getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));
        assertTrue(elapsed.compareTo(Duration.ofMillis(500)) < 0, "must not sleep past deadline, took " + elapsed);
    }

    @Test
    void retryDelay_waitsWithinBudgetBeforeRetrying() throws Exception {
        var client = mockClient();
        doReturn(failed(connectFailure()), success()).when(client)
                .getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));

        var store = new S3ContentStore(client, "bucket",
                new S3ReadRetryPolicy(1, Duration.ofSeconds(10), Duration.ofMillis(100), Duration.ofMillis(250)));
        var start = System.nanoTime();
        store.getReader(REFERENCE, null).getContentInputStream().close();
        var elapsed = Duration.ofNanos(System.nanoTime() - start);

        verify(client, times(2)).getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));
        assertTrue(elapsed.compareTo(Duration.ofMillis(90)) >= 0,
                "expected jittered backoff before retry, took " + elapsed);
    }

    @Test
    void configuredFixedDelay_isUsedWithinSharedBudget() throws Exception {
        var client = mockClient();
        doReturn(failed(connectFailure()), success()).when(client)
                .getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));
        var store = new S3ContentStore(client, "bucket", new S3ReadRetryPolicy(1, Duration.ofMillis(80),
                Duration.ofMillis(20), Duration.ofMillis(20)));
        var start = System.nanoTime();
        try (var input = store.getReader(REFERENCE, null).getContentInputStream()) {
            assertArrayEquals(CONTENT, input.readAllBytes());
        }
        assertTrue(Duration.ofNanos(System.nanoTime() - start).compareTo(Duration.ofMillis(15)) >= 0);
        verify(client, times(2)).getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));
    }

    @Test
    void configuredJitterBounds_areUsedForDelay() throws Exception {
        var client = mockClient();
        doReturn(failed(connectFailure()), success()).when(client)
                .getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));
        var store = new S3ContentStore(client, "bucket", new S3ReadRetryPolicy(1, Duration.ofSeconds(10),
                Duration.ofMillis(350), Duration.ofMillis(360)));
        var start = System.nanoTime();
        try (var input = store.getReader(REFERENCE, null).getContentInputStream()) {
            assertArrayEquals(CONTENT, input.readAllBytes());
        }
        assertTrue(Duration.ofNanos(System.nanoTime() - start).compareTo(Duration.ofMillis(340)) >= 0);
        verify(client, times(2)).getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));
    }

    // --- interruption ---

    @Test
    void preExistingInterruption_throwsWithoutAttemptAndKeepsFlag() {
        var client = mockClient();
        Thread.currentThread().interrupt();
        try {
            var store = new S3ContentStore(client, "bucket");
            assertThrows(UnreadableContentException.class, () -> store.getReader(REFERENCE, null));
            assertTrue(Thread.currentThread().isInterrupted(), "interrupted flag must be restored");
        } finally {
            Thread.interrupted();
        }
        verifyNoInteractions(client);
    }

    @Test
    void interruptionDuringWait_cancelsRestoresAndThrows() {
        var hung = new CompletableFuture<ResponseInputStream<GetObjectResponse>>();
        var client = mockClient();
        doReturn(hung).when(client).getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            scheduler.schedule(Thread.currentThread()::interrupt, 150, TimeUnit.MILLISECONDS);
            var store = new S3ContentStore(client, "bucket",
                    new S3ReadRetryPolicy(1, Duration.ofSeconds(10)));
            var thrown = assertThrows(UnreadableContentException.class, () -> store.getReader(REFERENCE, null));
            assertTrue(thrown.getCause() instanceof InterruptedException, "cause was " + thrown.getCause());
            assertTrue(Thread.currentThread().isInterrupted(), "interrupted flag must be restored");
            assertTrue(hung.isCancelled(), "abandoned operation must be cancelled");
        } finally {
            scheduler.shutdownNow();
            Thread.interrupted();
        }
    }

    @Test
    void interruptionDuringDelay_abortsRetriesRestoresAndThrows() {
        var client = mockClient();
        doReturn(failed(connectFailure())).when(client)
                .getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            // Use an explicit fixed delay so zero-minimum jitter cannot beat the scheduled interrupt.
            scheduler.schedule(Thread.currentThread()::interrupt, 30, TimeUnit.MILLISECONDS);
            var store = new S3ContentStore(client, "bucket",
                    new S3ReadRetryPolicy(3, Duration.ofSeconds(10), Duration.ofMillis(200), Duration.ofMillis(200)));
            var thrown = assertThrows(UnreadableContentException.class, () -> store.getReader(REFERENCE, null));
            assertTrue(thrown.getCause() instanceof InterruptedException, "cause was " + thrown.getCause());
            assertTrue(Thread.currentThread().isInterrupted(), "interrupted flag must be restored");
            verify(client, times(1)).getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));
        } finally {
            scheduler.shutdownNow();
            Thread.interrupted();
        }
    }

    // --- post-handoff behavior ---

    @Test
    void noDeadlineForOngoingDownload_streamReadableAfterBudget() throws Exception {
        var client = mockClient();
        doReturn(success()).when(client).getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));

        var store = new S3ContentStore(client, "bucket",
                new S3ReadRetryPolicy(1, Duration.ofMillis(100)));
        var startedNanos = System.nanoTime();
        var reader = store.getReader(REFERENCE, null);
        // Prove that acquisition budget has expired before consuming the handed-off stream.
        Awaitility.await().atMost(Duration.ofSeconds(2))
                .until(() -> System.nanoTime() - startedNanos > TimeUnit.MILLISECONDS.toNanos(100));
        try (var in = reader.getContentInputStream()) {
            assertArrayEquals(CONTENT, in.readAllBytes());
        }
        verify(client, times(1)).getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));
    }

    @Test
    void connectionFailureDuringStreamConsumption_isPropagatedWithoutRetry() throws Exception {
        var client = mockClient();
        var bodyFailure = new IOException("body connection failed",
                new io.netty.channel.ConnectTimeoutException("connection timed out"));
        var body = new InputStream() {
            @Override
            public int read() throws IOException {
                throw bodyFailure;
            }
        };
        var object = new ResponseInputStream<>(GetObjectResponse.builder().contentLength(1L).build(),
                AbortableInputStream.create(body));
        doReturn(CompletableFuture.completedFuture(object))
                .when(client).getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));

        var reader = new S3ContentStore(client, "bucket").getReader(REFERENCE, null);
        try (var input = reader.getContentInputStream()) {
            assertEquals(bodyFailure, assertThrows(IOException.class, input::read));
        }
        verify(client, times(1)).getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));
    }

    @Test
    void cancellationExceptionCause_isNotRetried() {
        var client = mockClient();
        doReturn(failed(new CompletionException(new CancellationException("cancelled"))))
                .when(client).getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));

        var store = new S3ContentStore(client, "bucket");
        assertThrows(UnreadableContentException.class, () -> store.getReader(REFERENCE, null));

        verify(client, times(1)).getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));
    }
}
