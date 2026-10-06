package com.contentgrid.appserver.contentstore.impl.s3;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adobe.testing.s3mock.testcontainers.S3MockContainer;
import com.contentgrid.appserver.autoconfigure.s3.testing.S3TestClients;
import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.UnreadableContentException;
import com.contentgrid.appserver.contentstore.api.range.ContentRangeRequest;
import com.contentgrid.appserver.contentstore.impl.utils.testing.S3MockUtils;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.ConnectException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Verifies the S3 read-acquisition retry contract against the real SDK (current BOM) with real Netty
 * networking over loopback, using only local fixtures: an S3Mock container and a plain-JDK TCP proxy.
 * <p>
 * Every connection failure asserted here is produced by the real Netty transport (nothing listening,
 * a blackholing socket, or a throttled forwarder) — never by injecting exceptions into a fake HTTP
 * transport. A refused connection (ECONNREFUSED) is deterministic on loopback; a genuine SYN-drop
 * connect <i>timeout</i> would require privileged network manipulation and is therefore NOT reproduced
 * here — the Netty {@code ConnectTimeoutException} class itself remains covered by the
 * {@code S3ContentStoreReadRetryTest} unit tests.
 */
@Testcontainers
@Timeout(value = 180)
class S3ReadRetryNettyIntegrationTest {

    @Container
    private static final S3MockContainer S3_MOCK = S3MockUtils.s3MockContainer();

    // --- fixtures ---

    private static String s3Endpoint() {
        return S3_MOCK.getHttpEndpoint();
    }

    private static int s3Port() {
        return URI.create(s3Endpoint()).getPort();
    }

    private static String bucketWithObject(byte[] content) {
        var client = S3TestClients.s3AsyncClient(s3Endpoint());
        var bucket = "netty-" + UUID.randomUUID();
        client.createBucket(CreateBucketRequest.builder().bucket(bucket).build()).join();
        client.putObject(PutObjectRequest.builder().bucket(bucket).key("object").build(),
                AsyncRequestBody.fromBytes(content)).join();
        client.close();
        return bucket;
    }

    private static byte[] randomBytes(int size) {
        var content = new byte[size];
        new java.util.Random(42).nextBytes(content);
        return content;
    }

    /** Outermost-client operation counter: counts SDK getObject OPERATIONS, not HTTP attempts. */
    private static final class Operations {
        final AtomicInteger calls = new AtomicInteger();
        final List<Throwable> failures = new CopyOnWriteArrayList<>();

        S3AsyncClient wrap(S3AsyncClient delegate) {
            InvocationHandler handler = (proxy, method, args) -> {
                if (method.getName().equals("getObject") && args != null && args.length == 2) {
                    calls.incrementAndGet();
                    try {
                        var result = (CompletableFuture<?>) method.invoke(delegate, args);
                        result.whenComplete((response, failure) -> {
                            if (failure != null) {
                                failures.add(failure);
                            }
                        });
                        return result;
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.getCause();
                    }
                }
                try {
                    return method.invoke(delegate, args);
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw e.getCause();
                }
            };
            return (S3AsyncClient) Proxy.newProxyInstance(S3ReadRetryNettyIntegrationTest.class.getClassLoader(),
                    new Class<?>[] { S3AsyncClient.class }, handler);
        }
    }

    private static boolean containsType(Throwable failure, Class<? extends Throwable> type) {
        for (var current = failure; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    // --- connection-refused: outer-operation bound with real Netty failures ---

    @Test
    void refusedConnection_retriesBoundedOuterOperationsWithRealConnectCause() {
        var deadPort = LocalTcpProxy.freePort();
        var operations = new Operations();
        var client = operations.wrap(S3TestClients.s3AsyncClient("http://127.0.0.1:" + deadPort));
        try {
            var store = new S3ContentStore(client, "bucket");
            var thrown = assertThrows(UnreadableContentException.class,
                    () -> store.getReader(ContentReference.of("object"), null));

            // default policy: one initial operation plus one retry
            assertEquals(2, operations.calls.get());
            assertEquals(2, operations.failures.size());
            // the failures are real: produced by the Netty transport against a dead port, not injected
            for (var failure : operations.failures) {
                assertTrue(containsType(failure, ConnectException.class),
                        "expected a real ConnectException in: " + failure);
                assertTrue(S3ConnectFailureClassifier.isRetryableConnectFailure(failure));
            }
            assertTrue(containsType(thrown, ConnectException.class));
        } finally {
            client.close();
        }
    }

    @Test
    void transientRefusedThenProxyUp_recoversWithinBoundedOperations() throws Exception {
        var content = randomBytes(64 * 1024);
        var bucket = bucketWithObject(content);
        var proxyPort = LocalTcpProxy.freePort();
        // DOWN: nothing listens on proxyPort yet, so the first operation fails refused for real.
        LocalTcpProxy proxy = new LocalTcpProxy(proxyPort, "127.0.0.1", s3Port(),
                LocalTcpProxy.Mode.FORWARD, 0);
        var operations = new Operations();
        var client = operations
                .wrap(S3TestClients.s3AsyncClient("http://127.0.0.1:" + proxyPort));
        try {
            var store = new S3ContentStore(client, bucket,
                    new S3ReadRetryPolicy(5, Duration.ofSeconds(25), Duration.ofMillis(100), Duration.ofMillis(250)));
            Future<byte[]> reader = CompletableFuture.supplyAsync(() -> {
                try (var stream = store.getReader(ContentReference.of("object"), null)
                        .getContentInputStream()) {
                    return stream.readAllBytes();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            // wait until the first attempt has really failed refused, still inside the retry backoff
            Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> !operations.failures.isEmpty());
            assertTrue(containsType(operations.failures.get(0), ConnectException.class));
            // recovery: bring the endpoint up; a later outer attempt must succeed through it
            proxy.start();

            assertArrayEquals(content, reader.get(30, TimeUnit.SECONDS));
            assertTrue(operations.calls.get() >= 2, "expected at least one retry, got " + operations.calls.get());
            assertTrue(operations.calls.get() <= 6, "expected at most maxRetries+1 operations, got "
                    + operations.calls.get());
        } finally {
            client.close();
            proxy.close();
        }
    }

    @Test
    void transientRefusedThenProxyUp_recoversMultipartDownloadWithinBoundedOperations() throws Exception {
        var content = randomBytes(20 * 1024 * 1024);
        var bucket = bucketWithObject(content);
        var proxyPort = LocalTcpProxy.freePort();
        // DOWN: nothing listens on proxyPort yet, so the first operation fails refused for real.
        LocalTcpProxy proxy = new LocalTcpProxy(proxyPort, "127.0.0.1", s3Port(),
                LocalTcpProxy.Mode.FORWARD, 0);
        var operations = new Operations();
        var client = operations
                .wrap(S3TestClients.s3AsyncClient("http://127.0.0.1:" + proxyPort));
        try {
            var store = new S3ContentStore(client, bucket,
                    new S3ReadRetryPolicy(5, Duration.ofSeconds(60), Duration.ofMillis(100), Duration.ofMillis(250)));
            Future<byte[]> reader = CompletableFuture.supplyAsync(() -> {
                try (var stream = store.getReader(ContentReference.of("object"), null)
                        .getContentInputStream()) {
                    return stream.readAllBytes();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            // wait until the first attempt has really failed refused, still inside the retry backoff
            Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> !operations.failures.isEmpty());
            assertTrue(containsType(operations.failures.get(0), ConnectException.class));
            // recovery: bring the endpoint up; a later outer attempt must succeed through it
            proxy.start();

            assertArrayEquals(content, reader.get(60, TimeUnit.SECONDS));
            assertTrue(operations.calls.get() >= 2, "expected at least one retry, got " + operations.calls.get());
            assertTrue(operations.calls.get() <= 6, "expected at most maxRetries+1 operations, got "
                    + operations.calls.get());
            // the recovery attempt fanned out into part downloads: the multipart path engaged
            Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertTrue(
                    proxy.requestHeads().stream().anyMatch(head -> head.contains("partNumber=")),
                    "no partNumber request observed: " + proxy.requestHeads()));
        } finally {
            client.close();
            proxy.close();
        }
    }

    // --- ranged reads: single outer operation, bound preserved when refused ---

    @Test
    void rangedRead_singleOuterOperationWithCorrectBytes() throws Exception {
        var content = randomBytes(1024 * 1024);
        var bucket = bucketWithObject(content);
        var operations = new Operations();
        var client = operations.wrap(S3TestClients.s3AsyncClient(s3Endpoint()));
        try {
            var store = new S3ContentStore(client, bucket);
            var range = ContentRangeRequest.createRange(0, 65_535).resolve(content.length);
            byte[] read;
            try (var stream = store.getReader(ContentReference.of("object"), range).getContentInputStream()) {
                read = stream.readAllBytes();
            }
            // ranged reads return an absolute-positioned partial view of the full content: the requested
            // bytes are served from the 206 response, everything outside the range reads as NUL bytes
            assertEquals(content.length, read.length);
            assertArrayEquals(java.util.Arrays.copyOfRange(content, 0, 65_536),
                    java.util.Arrays.copyOfRange(read, 0, 65_536));
            for (int i = 65_536; i < read.length; i++) {
                if (read[i] != 0) {
                    throw new AssertionError("expected NUL byte outside the range at " + i);
                }
            }
            // ranged requests intentionally take the single-part SDK path (no multipart fan-out);
            // SDK-internal retries still apply inside this one outer operation.
            assertEquals(1, operations.calls.get());
        } finally {
            client.close();
        }
    }

    @Test
    void rangedRead_refusedConnection_boundedOuterOperations() throws Exception {
        var deadPort = LocalTcpProxy.freePort();
        var operations = new Operations();
        var client = operations.wrap(S3TestClients.s3AsyncClient("http://127.0.0.1:" + deadPort));
        try {
            var store = new S3ContentStore(client, "bucket");
            var range = ContentRangeRequest.createRange(0, 100).resolve(1024);
            assertThrows(UnreadableContentException.class,
                    () -> store.getReader(ContentReference.of("object"), range));
            assertEquals(2, operations.calls.get());
        } finally {
            client.close();
        }
    }

    // --- blackhole: interruption and deadline close the actual Netty connection ---

    @Test
    void interruptionDuringAcquisition_closesActualConnectionAndKeepsClientUsable() throws Exception {
        var content = randomBytes(128 * 1024);
        var bucket = bucketWithObject(content);
        LocalTcpProxy proxy = new LocalTcpProxy(LocalTcpProxy.freePort(), "127.0.0.1", s3Port(),
                LocalTcpProxy.Mode.BLACKHOLE, 0);
        proxy.start();
        var client = S3TestClients.s3AsyncClient("http://127.0.0.1:" + proxy.port());
        try {
            var store = new S3ContentStore(client, bucket,
                    new S3ReadRetryPolicy(1, Duration.ofSeconds(30)));
            var outcome = new CompletableFuture<Throwable>();
            var interruptedFlag = new CompletableFuture<Boolean>();
            var thread = new Thread(() -> {
                try {
                    store.getReader(ContentReference.of("object"), null);
                    outcome.complete(null);
                } catch (UnreadableContentException e) {
                    outcome.complete(e);
                } finally {
                    interruptedFlag.complete(Thread.currentThread().isInterrupted());
                }
            });
            thread.start();
            assertTrue(proxy.awaitAccepted(10, TimeUnit.SECONDS), "client never connected");
            Awaitility.await().atMost(Duration.ofSeconds(10))
                    .until(() -> !proxy.requestHeads().isEmpty());
            thread.interrupt();
            var thrown = outcome.get(15, TimeUnit.SECONDS);
            assertTrue(thrown instanceof UnreadableContentException, "outcome was " + thrown);
            assertTrue(containsType(thrown, InterruptedException.class), "cause was " + thrown.getCause());
            assertTrue(interruptedFlag.get(5, TimeUnit.SECONDS), "interrupted flag must be restored");
            // the blackholed socket observed EOF: cancelling the outer future closed the real connection
            assertTrue(proxy.awaitPeerClose(10, TimeUnit.SECONDS), "actual connection was not closed");

            // the client is not poisoned by the abandoned attempt: it serves through the same proxy
            proxy.setMode(LocalTcpProxy.Mode.FORWARD);
            try (var stream = store.getReader(ContentReference.of("object"), null).getContentInputStream()) {
                assertArrayEquals(content, stream.readAllBytes());
            }
        } finally {
            client.close();
            proxy.close();
        }
    }

    @Test
    void deadlineDuringAcquisition_abandonsAndClosesActualConnection() throws Exception {
        var content = randomBytes(64 * 1024);
        var bucket = bucketWithObject(content);
        LocalTcpProxy proxy = new LocalTcpProxy(LocalTcpProxy.freePort(), "127.0.0.1", s3Port(),
                LocalTcpProxy.Mode.BLACKHOLE, 0);
        proxy.start();
        var client = S3TestClients.s3AsyncClient("http://127.0.0.1:" + proxy.port());
        try {
            var store = new S3ContentStore(client, bucket,
                    new S3ReadRetryPolicy(0, Duration.ofMillis(500)));
            var start = System.nanoTime();
            var thrown = assertThrows(UnreadableContentException.class,
                    () -> store.getReader(ContentReference.of("object"), null));
            var elapsed = Duration.ofNanos(System.nanoTime() - start);
            assertTrue(containsType(thrown, TimeoutException.class), "cause was " + thrown.getCause());
            assertTrue(elapsed.compareTo(Duration.ofSeconds(10)) < 0, "must fail via deadline, took " + elapsed);
            assertTrue(proxy.awaitPeerClose(10, TimeUnit.SECONDS), "actual connection was not closed");

            proxy.setMode(LocalTcpProxy.Mode.FORWARD);
            try (var stream = store.getReader(ContentReference.of("object"), null).getContentInputStream()) {
                assertArrayEquals(content, stream.readAllBytes());
            }
        } finally {
            client.close();
            proxy.close();
        }
    }

    // --- multipart: fan-out under one outer operation; abandonment closes part connections ---

    @Test
    void multipartFullDownload_singleOuterOperationDespitePartFanOut() throws Exception {
        var content = randomBytes(20 * 1024 * 1024);
        var bucket = bucketWithObject(content);
        LocalTcpProxy proxy = new LocalTcpProxy(LocalTcpProxy.freePort(), "127.0.0.1", s3Port(),
                LocalTcpProxy.Mode.FORWARD, 1);
        proxy.start();
        var operations = new Operations();
        var client = operations.wrap(S3TestClients.s3AsyncClient("http://127.0.0.1:" + proxy.port()));
        try {
            var store = new S3ContentStore(client, bucket,
                    new S3ReadRetryPolicy(0, Duration.ofSeconds(60)));
            byte[] read;
            try (var stream = store.getReader(ContentReference.of("object"), null).getContentInputStream()) {
                read = stream.readAllBytes();
            }
            assertArrayEquals(content, read);
            // the SDK fanned the large download out with partNumber requests: the multipart path engaged,
            // yet the retry bound counts SDK operations, not the inner part HTTP requests
            Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertTrue(
                    proxy.requestHeads().stream().anyMatch(head -> head.contains("partNumber=")),
                    "no partNumber request observed: " + proxy.requestHeads()));
            assertEquals(1, operations.calls.get());
        } finally {
            client.close();
            proxy.close();
        }
    }

    @Test
    void multipartAcquisitionBlocked_deadlineAbandonsAndClosesPartConnection() throws Exception {
        var content = randomBytes(20 * 1024 * 1024);
        var bucket = bucketWithObject(content);
        // BLACKHOLE: part requests are accepted but never answered, so acquisition never completes
        LocalTcpProxy proxy = new LocalTcpProxy(LocalTcpProxy.freePort(), "127.0.0.1", s3Port(),
                LocalTcpProxy.Mode.BLACKHOLE, 0);
        proxy.start();
        var operations = new Operations();
        var client = operations.wrap(S3TestClients.s3AsyncClient("http://127.0.0.1:" + proxy.port()));
        try {
            var store = new S3ContentStore(client, bucket,
                    new S3ReadRetryPolicy(0, Duration.ofSeconds(2)));
            var closesBefore = proxy.peerCloseCount();
            var start = System.nanoTime();
            var thrown = assertThrows(UnreadableContentException.class,
                    () -> store.getReader(ContentReference.of("object"), null));
            var elapsed = Duration.ofNanos(System.nanoTime() - start);
            assertTrue(containsType(thrown, TimeoutException.class), "cause was " + thrown.getCause());
            assertTrue(elapsed.compareTo(Duration.ofSeconds(10)) < 0, "must fail via deadline, took " + elapsed);
            assertEquals(1, operations.calls.get());
            // a partNumber request was issued and hung: the serial multipart path engaged
            Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertTrue(
                    proxy.requestHeads().stream().anyMatch(head -> head.contains("partNumber=")),
                    "no partNumber request observed: " + proxy.requestHeads()));
            // cancelling the outer future propagated through the splitting transformer into the multipart
            // downloader: the in-flight part request was cancelled and its socket closed for real
            assertTrue(proxy.awaitPeerClosesGreaterThan(closesBefore, 15, TimeUnit.SECONDS),
                    "multipart part connection was not closed");

            // the same store and client recover once the endpoint answers and stream the whole object
            proxy.setMode(LocalTcpProxy.Mode.FORWARD);
            try (var stream = store.getReader(ContentReference.of("object"), null).getContentInputStream()) {
                assertArrayEquals(content, stream.readAllBytes());
            }
        } finally {
            client.close();
            proxy.close();
        }
    }

    // --- post-handoff: a slow download outlives the acquisition deadline ---

    @Test
    void slowDownloadAfterHandoff_outlivesAcquisitionDeadline() throws Exception {
        var content = randomBytes(300 * 1024);
        var bucket = bucketWithObject(content);
        LocalTcpProxy proxy = new LocalTcpProxy(LocalTcpProxy.freePort(), "127.0.0.1", s3Port(),
                LocalTcpProxy.Mode.FORWARD, 50);
        proxy.start();
        var client = S3TestClients.s3AsyncClient("http://127.0.0.1:" + proxy.port());
        try {
            // ~38 throttled 8 KiB chunks take ~2s; the acquisition deadline is long gone by then
            var store = new S3ContentStore(client, bucket,
                    new S3ReadRetryPolicy(0, Duration.ofMillis(500)));
            var start = System.nanoTime();
            var reader = store.getReader(ContentReference.of("object"), null);
            byte[] read;
            try (var stream = reader.getContentInputStream()) {
                read = stream.readAllBytes();
            }
            var elapsed = Duration.ofNanos(System.nanoTime() - start);
            assertArrayEquals(content, read);
            assertTrue(elapsed.compareTo(Duration.ofMillis(500)) > 0,
                    "download should outlive the acquisition deadline, took " + elapsed);
        } finally {
            client.close();
            proxy.close();
        }
    }
}
