package com.contentgrid.appserver.autoconfigure.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.contentgrid.appserver.autoconfigure.s3.testing.S3LoopbackServer;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;

class S3ContentStoreClientsTest {

    /** Bound for each SDK future: a hung transport must fail the test, not the suite. */
    private static final long SDK_FUTURE_TIMEOUT_SECONDS = 60;

    private S3LoopbackServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = S3LoopbackServer.start();
        server.seed("bucket", "key", "content".getBytes(StandardCharsets.UTF_8));
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private S3ContentStoreClients clients() {
        var httpClient = S3ClientFactory.createSharedHttpClient(0, 1);
        var readClient = S3ClientFactory.createS3ReadAsyncClient(server.endpoint(), "test", "test", null,
                true, httpClient, false);
        var writeClient = S3ClientFactory.createS3WriteAsyncClient(server.endpoint(), "test", "test", null,
                true, httpClient, false);
        return new S3ContentStoreClients(readClient, writeClient, httpClient);
    }

    @Test
    void readAndWriteClients_serveRequestsThroughSharedTransport() throws Exception {
        try (var clients = clients()) {
            assertThat(clients.readClient()).isNotNull();
            assertThat(clients.writeClient()).isNotNull();
            assertThat(clients.readClient()).isNotSameAs(clients.writeClient());
            assertThat(clients.httpClient()).isNotNull();

            try (var response = clients.readClient().getObject(
                    GetObjectRequest.builder().bucket("bucket").key("key").build(),
                    AsyncResponseTransformer.toBlockingInputStream()).orTimeout(SDK_FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS).join()) {
                assertThat(response.readAllBytes()).isEqualTo("content".getBytes(StandardCharsets.UTF_8));
            }
            clients.writeClient().putObject(
                    PutObjectRequest.builder().bucket("bucket").key("written").build(),
                    AsyncRequestBody.fromBytes("written".getBytes(StandardCharsets.UTF_8))).orTimeout(SDK_FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS).join();
        }
    }

    /**
     * Transport decorator counting executions. Proves which S3 client routes through it without any
     * timing assumptions.
     */
    private static final class CountingHttpClient implements SdkAsyncHttpClient {
        private final SdkAsyncHttpClient delegate;
        private final List<String> order;
        private final AtomicInteger executions = new AtomicInteger();
        private final AtomicInteger closes = new AtomicInteger();

        private CountingHttpClient(SdkAsyncHttpClient delegate, List<String> order) {
            this.delegate = delegate;
            this.order = order;
        }

        @Override
        public CompletableFuture<Void> execute(AsyncExecuteRequest request) {
            executions.incrementAndGet();
            return delegate.execute(request);
        }

        @Override
        public void close() {
            closes.incrementAndGet();
            order.add("http");
            delegate.close();
        }

        @Override
        public String clientName() {
            return delegate.clientName();
        }
    }

    /**
     * Wraps an S3 client in a proxy that records {@code close()} calls while forwarding everything
     * else. The order list receives {@code name} once per close call.
     */
    private static S3AsyncClient closeRecordingClient(S3AsyncClient delegate, String name,
            List<String> order) {
        return (S3AsyncClient) Proxy.newProxyInstance(S3ContentStoreClientsTest.class.getClassLoader(),
                new Class<?>[] {S3AsyncClient.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("close")) {
                        order.add(name);
                    }
                    return method.invoke(delegate, args);
                });
    }

    @Test
    void bothClientsExecuteThroughSharedTransport() throws Exception {
        var delegate = S3ClientFactory.createSharedHttpClient(0, 1);
        var counting = new CountingHttpClient(delegate, new ArrayList<>());
        var readClient = S3ClientFactory.createS3ReadAsyncClient(server.endpoint(), "test", "test", null,
                true, counting, false);
        var writeClient = S3ClientFactory.createS3WriteAsyncClient(server.endpoint(), "test", "test",
                null, true, counting, false);
        try (var clients = new S3ContentStoreClients(readClient, writeClient, counting)) {
            try (var response = clients.readClient().getObject(
                    GetObjectRequest.builder().bucket("bucket").key("key").build(),
                    AsyncResponseTransformer.toBlockingInputStream()).orTimeout(SDK_FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS).join()) {
                assertThat(response.readAllBytes())
                        .isEqualTo("content".getBytes(StandardCharsets.UTF_8));
            }
            clients.writeClient().putObject(
                    PutObjectRequest.builder().bucket("bucket").key("written").build(),
                    AsyncRequestBody.fromBytes("written".getBytes(StandardCharsets.UTF_8))).orTimeout(SDK_FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS).join();
            // One read plus one write, both through the single shared transport instance
            assertThat(counting.executions.get()).isEqualTo(2);
        }
    }

    @Test
    void close_closesClientsBeforeTransportExactlyOnce() throws Exception {
        var delegate = S3ClientFactory.createSharedHttpClient(0, 1);
        var order = new CopyOnWriteArrayList<String>();
        var counting = new CountingHttpClient(delegate, order);
        var readClient = closeRecordingClient(
                S3ClientFactory.createS3ReadAsyncClient(server.endpoint(), "test", "test", null, true,
                        counting, false),
                "read", order);
        var writeClient = closeRecordingClient(
                S3ClientFactory.createS3WriteAsyncClient(server.endpoint(), "test", "test", null, true,
                        counting, false),
                "write", order);
        var clients = new S3ContentStoreClients(readClient, writeClient, counting);

        clients.close();

        // S3 clients first (either order), shared transport last, each exactly once ...
        assertThat(order.subList(0, 2)).containsExactlyInAnyOrder("read", "write");
        assertThat(order).hasSize(3);
        assertThat(order.get(2)).isEqualTo("http");
        assertThat(counting.closes.get()).isEqualTo(1);
        // ... and repeating close stays a safe no-op
        assertThatNoException().isThrownBy(clients::close);
        assertThat(order).hasSize(3);
        assertThat(counting.closes.get()).isEqualTo(1);
        assertThatNoException().isThrownBy(counting::close);
    }
}
