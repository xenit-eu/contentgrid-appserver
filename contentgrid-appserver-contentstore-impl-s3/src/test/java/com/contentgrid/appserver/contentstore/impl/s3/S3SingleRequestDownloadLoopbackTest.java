package com.contentgrid.appserver.contentstore.impl.s3;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.contentgrid.appserver.autoconfigure.s3.S3ClientFactory;
import com.contentgrid.appserver.autoconfigure.s3.testing.S3LoopbackServer;
import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.range.ContentRangeRequest;
import com.contentgrid.appserver.contentstore.api.range.ResolvedContentRange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.services.s3.S3AsyncClient;

/**
 * Proves single-request download behavior against a real SDK client talking to a loopback S3 stand-in:
 * full downloads are one plain GET without {@code partNumber}, explicit ranges are preserved, empty
 * objects stream correctly, and a transient pre-header connection failure is recovered by native SDK
 * retries without any read retry wrapper in {@link S3ContentStore}.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class S3SingleRequestDownloadLoopbackTest {

    private static final String BUCKET = "bucket";
    private static final String KEY = "object-key";
    private static final byte[] CONTENT = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    /**
     * Hard bound for each blocking store call. The store blocks in {@code join()}, which ignores
     * interrupts, so without this a hung SDK future would hang the suite forever; the class-level
     * {@code Timeout} above is only a backstop.
     */
    private static final long BLOCKING_CALL_TIMEOUT_SECONDS = 60;

    private S3LoopbackServer server;
    private SdkAsyncHttpClient httpClient;
    private S3AsyncClient readClient;

    @BeforeEach
    void setUp() throws Exception {
        server = S3LoopbackServer.start();
        server.seed(BUCKET, KEY, CONTENT);
        httpClient = S3ClientFactory.createSharedHttpClient(0, 1);
        readClient = S3ClientFactory.createS3ReadAsyncClient(server.endpoint(), "test", "test", null,
                true, httpClient, false);
    }

    @AfterEach
    void tearDown() {
        readClient.close();
        httpClient.close();
        server.close();
    }

    private S3ContentStore store() {
        // Downloads under test only need the read client; wiring the same client for writes keeps the
        // test focused on download wire behavior without involving a second client.
        return new S3ContentStore(readClient, readClient, BUCKET);
    }

    /**
     * Runs a blocking store call off the test thread with a hard bound (see
     * {@code BLOCKING_CALL_TIMEOUT_SECONDS}).
     */
    private static <T> T bounded(Callable<T> blockingCall) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return blockingCall.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }).orTimeout(BLOCKING_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS).join();
    }

    private byte[] readContent(ContentReference reference, ResolvedContentRange range) {
        return bounded(() -> {
            try (var stream = store().getReader(reference, range).getContentInputStream()) {
                return stream.readAllBytes();
            }
        });
    }

    @Test
    void fullDownload_isSingleGetWithoutPartNumber() {
        assertArrayEquals(CONTENT, readContent(ContentReference.of(KEY), null));

        var gets = server.requestsFor("GET");
        assertEquals(1, gets.size(), "full download must be a single GET, got: " + server.requests());
        assertTrue(server.requests().stream().noneMatch(S3LoopbackServer.RecordedRequest::hasPartNumber),
                "no request may carry partNumber, got: " + server.requests());
    }

    @Test
    void rangedDownload_preservesRangeHeader() throws Exception {
        var range = ContentRangeRequest.createRange(4, 7).resolve(CONTENT.length);
        var bytes = readContent(ContentReference.of(KEY), range);
        assertEquals(CONTENT.length, bytes.length);
        assertArrayEquals("4567".getBytes(StandardCharsets.UTF_8),
                java.util.Arrays.copyOfRange(bytes, 4, 8));

        var gets = server.requestsFor("GET");
        assertEquals(1, gets.size());
        assertEquals("bytes=4-7", gets.get(0).rangeHeader());
    }

    @Test
    void emptyObject_streamsWithoutError() {
        server.seed(BUCKET, "empty", new byte[0]);

        assertEquals(-1, bounded(() -> {
            try (var stream = store().getReader(ContentReference.of("empty"), null)
                    .getContentInputStream()) {
                return stream.read();
            }
        }));

        assertEquals(1, server.requestsFor("GET").size());
        assertTrue(server.requests().stream().noneMatch(S3LoopbackServer.RecordedRequest::hasPartNumber));
    }

    /**
     * HTTP transport failing the first attempt with an {@link IOException} before any response headers
     * are received, while counting executions. Recovery must come from native SDK retries: the store
     * itself contains no retry loop.
     * <p>
     * The async transport contract requires signalling the failure through
     * {@code request.responseHandler().onError(...)} in addition to failing the returned future (this is
     * what the Netty transport does in {@code NettyRequestExecutor.handleFailure}). Failing only the
     * future leaves the SDK response machinery waiting forever, which hangs {@code join()} in the store.
     */
    private static final class FailFirstHttpClient implements SdkAsyncHttpClient {
        private final SdkAsyncHttpClient delegate;
        private final AtomicInteger attempts = new AtomicInteger();

        private FailFirstHttpClient(SdkAsyncHttpClient delegate) {
            this.delegate = delegate;
        }

        @Override
        public CompletableFuture<Void> execute(AsyncExecuteRequest request) {
            if (attempts.getAndIncrement() == 0) {
                var error = new IOException("simulated pre-header connection failure");
                request.responseHandler().onError(error);
                return CompletableFuture.failedFuture(error);
            }
            return delegate.execute(request);
        }

        @Override
        public void close() {
            delegate.close();
        }

        @Override
        public String clientName() {
            return delegate.clientName();
        }

        int attempts() {
            return attempts.get();
        }
    }

    @Test
    void transientPreHeaderFailure_recoversThroughNativeSdkRetries() {
        var flakyTransport = new FailFirstHttpClient(httpClient);
        try (var retryingClient = S3ClientFactory.createS3ReadAsyncClient(server.endpoint(), "test", "test",
                null, true, flakyTransport, false)) {
            var store = new S3ContentStore(retryingClient, retryingClient, BUCKET);
            var content = bounded(() -> {
                try (var stream = store.getReader(ContentReference.of(KEY), null).getContentInputStream()) {
                    return stream.readAllBytes();
                }
            });
            assertArrayEquals(CONTENT, content);
        }

        assertEquals(2, flakyTransport.attempts(),
                "SDK retries must transparently re-execute the single GET exactly once");
        var gets = server.requestsFor("GET");
        assertEquals(1, gets.size(), "only the retried attempt reaches the server, got: " + server.requests());
        assertTrue(server.requests().stream().noneMatch(S3LoopbackServer.RecordedRequest::hasPartNumber));
    }
}
