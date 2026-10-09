package com.contentgrid.appserver.contentstore.impl.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adobe.testing.s3mock.testcontainers.S3MockContainer;
import com.contentgrid.appserver.autoconfigure.s3.testing.S3TestClients;
import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.UnreadableContentException;
import com.contentgrid.appserver.contentstore.impl.s3.LocalTcpProxy.Mode;
import com.contentgrid.appserver.contentstore.impl.utils.testing.S3MockUtils;
import io.netty.handler.timeout.ReadTimeoutException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.AllArgsConstructor;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.retries.api.BackoffStrategy;
import software.amazon.awssdk.retries.api.RetryStrategy;
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
 * here.
 */
@Testcontainers
@Timeout(value = 180)
class S3ReadRetryIntegrationTest {

    @Container
    private static final S3MockContainer S3_MOCK = S3MockUtils.s3MockContainer();

    private LocalTcpProxy tcpProxy;
    private S3ContentStore contentStore;
    private S3AsyncClient s3Client;
    private RetryStrategy retryStrategy;

    @AllArgsConstructor
    private static class RandomInputStream extends InputStream {

        private long remaining;

        @Override
        public int read() {
            if(--remaining < 0) {
                return -1;
            }
            return Byte.toUnsignedInt((byte) remaining);
        }
    }


    @BeforeEach
    void setupClient() {
        var port = LocalTcpProxy.freePort();
        tcpProxy = new LocalTcpProxy(port, S3_MOCK.getHost(), S3_MOCK.getHttpServerPort(), Mode.FORWARD, 0);
        s3Client = S3TestClients.createFactory("http://127.0.0.1:"+port)
                .customizeClientBuilder(builder -> {
                    builder.overrideConfiguration(config -> {
                        // Insert a spy on the retry strategy
                        config.retryStrategy(spyRetryStrategy(StandardRetryStrategy.builder()
                                .backoffStrategy(BackoffStrategy.fixedDelay(Duration.ofMillis(500)))
                                .maxAttempts(3)
                                .build()));
                    });
                })
                .createClient();


        // Create bucket with an object
        var bucket = "netty-" + UUID.randomUUID();
        var tempClient = S3TestClients.s3AsyncClient(S3_MOCK.getHttpEndpoint());
        tempClient.createBucket(CreateBucketRequest.builder().bucket(bucket).build()).join();
        tempClient.putObject(PutObjectRequest.builder().bucket(bucket).key("object").build(),
                AsyncRequestBody.fromInputStream(new RandomInputStream(100L*1024*1024), 100L*1024*1024)).join();
        tempClient.close();


        contentStore = new S3ContentStore(s3Client, bucket);
    }

    /**
     * Wraps the retry strategy in a spy, and keeps it a spy when the SDK rebuilds it via
     * {@code toBuilder()...build()} (e.g. to apply client defaults).
     * The {@link #retryStrategy} field always tracks the most recently built spy.
     */
    private RetryStrategy spyRetryStrategy(RetryStrategy strategy) {
        var spy = Mockito.spy(strategy);
        Mockito.doAnswer(toBuilderInvocation -> {
            var builder = Mockito.spy((RetryStrategy.Builder<?, ?>) toBuilderInvocation.callRealMethod());
            Mockito.doAnswer(buildInvocation -> spyRetryStrategy((RetryStrategy) buildInvocation.callRealMethod()))
                    .when(builder).build();
            return builder;
        }).when(spy).toBuilder();
        retryStrategy = spy;
        return spy;
    }

    @AfterEach
    void cleanup() {
        tcpProxy.close();
        s3Client.close();
    }

    @Test
    void permanentlyRefusedConnectionThrows() {
        assertThatThrownBy(() -> contentStore.getReader(ContentReference.of("object"), null))
                .isInstanceOf(UnreadableContentException.class)
                .hasRootCauseInstanceOf(ConnectException.class);
    }

    @Test
    void transientRefusedConnectionRetries() throws Exception {
        Future<byte[]> reader = CompletableFuture.supplyAsync(() -> {
            try (var stream = contentStore.getReader(ContentReference.of("object"), null)
                    .getContentInputStream()) {
                return stream.readAllBytes();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Awaitility.await("for a retry to have happened")
                .atMost(Duration.ofSeconds(10))
                .until(() -> {
                    var mockingDetails = Mockito.mockingDetails(retryStrategy);
                    var invocations =  mockingDetails.getInvocations().stream()
                            .filter(invocation -> invocation.getMethod().getName().startsWith("refreshRetryToken"))
                            .toList();

                    // If there is an invocation to refreshRetryToken, a retry is already being attempted
                    return !invocations.isEmpty();
                });

        // recovery: bring the endpoint up; a later outer attempt must succeed through it
        tcpProxy.start();

        assertThatCode(() -> reader.get(30, TimeUnit.SECONDS))
                .doesNotThrowAnyException();
    }

    @Test
    void readTimeoutDuringInitialResponse() throws Exception {
        tcpProxy.setMode(Mode.BLACKHOLE);
        tcpProxy.start();

        assertThatThrownBy(() -> contentStore.getReader(ContentReference.of("object"), null))
                .isInstanceOf(UnreadableContentException.class)
                .satisfies(unreadableException -> {
                    assertThat(unreadableException.getCause())
                            .isInstanceOfSatisfying(SdkClientException.class, sdkClientException -> {
                                assertThat(sdkClientException.numAttempts()).isGreaterThan(1);
                            });
                });

        assertThat(tcpProxy.awaitPeerClose(10, TimeUnit.SECONDS))
                .as("actual connection must be closed")
                .isTrue();

        tcpProxy.setMode(LocalTcpProxy.Mode.FORWARD);
        try (var stream = contentStore.getReader(ContentReference.of("object"), null).getContentInputStream()) {
            assertThatCode(stream::readAllBytes).doesNotThrowAnyException();
        }
    }

    // --- post-handoff: a slow download outlives the acquisition deadline ---

    @Test
    void slowDownloadAfterInitialResponse() throws Exception {
        tcpProxy.setChunkDelayMillis(50);
        tcpProxy.start();
        // ~1024 throttled 8 KiB chunks take ~5s; the 2s acquisition deadline is long gone by then

        var ref = contentStore.writeContent(new RandomInputStream(8*1024*1024)).getReference();

        var start = System.nanoTime();
        var reader = contentStore.getReader(ref, null);
        try (var stream = reader.getContentInputStream()) {
            var block = new byte[1024*1024];
            while(stream.read(block) != -1);
        }
        var elapsed = Duration.ofNanos(System.nanoTime() - start);
        assertThat(elapsed)
                .as("download should outlive the acquisition deadline")
                .isGreaterThan(Duration.ofSeconds(2));
    }

    // --- post-handoff: failures while streaming the body surface to the reader and are never retried ---

    @Test
    void connectionDroppedDuringDownload() throws Exception {
        tcpProxy.start();
        try (var stream = contentStore.getReader(ContentReference.of("object"), null).getContentInputStream()) {
            assertThat(stream.readNBytes(1024 * 1024)).hasSize(1024 * 1024);

            tcpProxy.close();

            assertThatThrownBy(() -> stream.transferTo(OutputStream.nullOutputStream()))
                    .as("a truncated body must fail, not end in a clean EOF")
                    .isInstanceOf(IOException.class);
        }

        assertThat(tcpProxy.requestHeads())
                .as("a download that already started streaming must not be retried")
                .hasSize(1);
    }

    @Test
    void readTimeoutDuringDownload() throws Exception {
        tcpProxy.start();
        try (var stream = contentStore.getReader(ContentReference.of("object"), null).getContentInputStream()) {
            assertThat(stream.readNBytes(1024 * 1024)).hasSize(1024 * 1024);

            tcpProxy.setMode(Mode.BLACKHOLE);

            assertThatThrownBy(() -> stream.transferTo(OutputStream.nullOutputStream()))
                    .isInstanceOf(IOException.class)
                    .hasRootCauseInstanceOf(ReadTimeoutException.class);
        }

        assertThat(tcpProxy.awaitPeerClose(10, TimeUnit.SECONDS))
                .as("actual connection must be closed")
                .isTrue();
        assertThat(tcpProxy.requestHeads())
                .as("a download that already started streaming must not be retried")
                .hasSize(1);
    }
}
