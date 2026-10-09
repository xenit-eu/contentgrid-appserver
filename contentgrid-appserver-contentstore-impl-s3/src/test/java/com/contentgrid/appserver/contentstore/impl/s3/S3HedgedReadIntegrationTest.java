package com.contentgrid.appserver.contentstore.impl.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adobe.testing.s3mock.testcontainers.S3MockContainer;
import com.contentgrid.appserver.autoconfigure.s3.testing.S3TestClients;
import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.UnreadableContentException;
import com.contentgrid.appserver.contentstore.impl.s3.LocalTcpProxy.Mode;
import com.contentgrid.appserver.contentstore.impl.s3.SimpleOperationHedger.HedgingSettings;
import com.contentgrid.appserver.contentstore.impl.utils.testing.S3MockUtils;
import java.time.Duration;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Verifies hedged reads of the {@link S3ContentStore} against the real SDK with real Netty networking over
 * loopback, using an S3Mock container behind a plain-JDK TCP proxy that makes individual requests slow or hang.
 * <p>
 * The client does not pool connections, so every request (hedges and SDK retries alike) uses its own connection.
 * The proxy numbers requests in the order in which they arrive.
 */
@Testcontainers
@Timeout(value = 180)
class S3HedgedReadIntegrationTest {

    @Container
    private static final S3MockContainer S3_MOCK = S3MockUtils.s3MockContainer();

    private record Settings(Duration attemptDelay, Duration operationTimeout, int maxAttempts)
            implements HedgingSettings {

    }

    private static final Duration ATTEMPT_OFFSET = Duration.ofMillis(200);

    /**
     * Response delay of a slow request: well beyond the attempt offset, and far longer than a hedged read takes
     */
    private static final long SLOW_RESPONSE_MILLIS = 10_000;

    private static final ContentReference OBJECT = ContentReference.of("object");

    private static final byte[] CONTENT = new byte[4 * 1024 * 1024];

    static {
        new Random(42).nextBytes(CONTENT);
    }

    private LocalTcpProxy tcpProxy;
    private S3AsyncClient s3Client;
    private String bucket;

    @BeforeEach
    void setup() throws Exception {
        var port = LocalTcpProxy.freePort();
        tcpProxy = new LocalTcpProxy(port, S3_MOCK.getHost(), S3_MOCK.getHttpServerPort(), Mode.FORWARD, 0);
        tcpProxy.start();
        s3Client = S3TestClients.s3AsyncClient("http://127.0.0.1:" + port);

        // Create bucket with an object, directly against S3Mock, so the proxy only observes the reads
        bucket = "hedged-" + UUID.randomUUID();
        try (var directClient = S3TestClients.s3AsyncClient(S3_MOCK.getHttpEndpoint())) {
            directClient.createBucket(CreateBucketRequest.builder().bucket(bucket).build()).join();
            directClient.putObject(PutObjectRequest.builder().bucket(bucket).key(OBJECT.getValue()).build(),
                    AsyncRequestBody.fromBytes(CONTENT)).join();
        }
    }

    @AfterEach
    void cleanup() {
        tcpProxy.close();
        s3Client.close();
    }

    private S3ContentStore contentStore(Duration operationTimeout, int maxAttempts) {
        return new S3ContentStore(s3Client, bucket,
                new SimpleOperationHedger(new Settings(ATTEMPT_OFFSET, operationTimeout, maxAttempts)));
    }

    private S3ContentStore contentStore() {
        return contentStore(Duration.ofSeconds(30), 3);
    }

    private static byte[] readFully(S3ContentStore contentStore) throws Exception {
        try (var stream = contentStore.getReader(OBJECT, null).getContentInputStream()) {
            return stream.readAllBytes();
        }
    }

    @Test
    void fastFirstRequest_isNotHedged() throws Exception {
        assertThat(readFully(contentStore())).isEqualTo(CONTENT);

        assertThat(tcpProxy.requestHeads())
                .as("a request that responds within the attempt offset must not be hedged")
                .hasSize(1);
    }

    @Test
    void slowFirstResponse_hedgeWins() throws Exception {
        tcpProxy.setFirstResponseDelayMillis(request -> request == 1 ? SLOW_RESPONSE_MILLIS : 0);
        var contentStore = contentStore();

        var start = System.nanoTime();
        var reader = contentStore.getReader(OBJECT, null);
        var elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(elapsed)
                .as("the hedged request must respond long before the slow request would")
                .isLessThan(Duration.ofMillis(SLOW_RESPONSE_MILLIS / 2));
        assertThat(tcpProxy.requestHeads()).hasSize(2);
        assertThat(tcpProxy.awaitPeerClose(1, 10, TimeUnit.SECONDS))
                .as("the connection of the slow request must be closed")
                .isTrue();

        try (var stream = reader.getContentInputStream()) {
            assertThat(stream.readAllBytes()).isEqualTo(CONTENT);
        }
    }

    @Test
    void hangingFirstRequest_hedgeWins() throws Exception {
        tcpProxy.setRequestMode(request -> request == 1 ? Mode.BLACKHOLE : Mode.FORWARD);
        var contentStore = contentStore();

        var start = System.nanoTime();
        var reader = contentStore.getReader(OBJECT, null);
        var elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(elapsed)
                .as("the hedged request must respond without waiting for the hanging request to time out")
                .isLessThan(Duration.ofSeconds(5));
        assertThat(tcpProxy.requestHeads()).hasSize(2);
        assertThat(tcpProxy.awaitPeerClose(1, 10, TimeUnit.SECONDS))
                .as("the connection of the hanging request must be closed")
                .isTrue();

        try (var stream = reader.getContentInputStream()) {
            assertThat(stream.readAllBytes()).isEqualTo(CONTENT);
        }
    }

    @Test
    void slowFirstAndSecondResponse_thirdHedgeWins() throws Exception {
        tcpProxy.setFirstResponseDelayMillis(request -> request <= 2 ? SLOW_RESPONSE_MILLIS : 0);

        assertThat(readFully(contentStore())).isEqualTo(CONTENT);

        assertThat(tcpProxy.requestHeads()).hasSize(3);
        assertThat(tcpProxy.awaitPeerClose(1, 10, TimeUnit.SECONDS)).isTrue();
        assertThat(tcpProxy.awaitPeerClose(2, 10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void firstRequestRespondsWhileHedgeInFlight_firstWins() throws Exception {
        // The first request is slower than the attempt offset, but still faster than the hedge
        tcpProxy.setFirstResponseDelayMillis(request -> switch (request) {
            case 1 -> ATTEMPT_OFFSET.toMillis() * 3;
            default -> SLOW_RESPONSE_MILLIS;
        });

        var start = System.nanoTime();
        assertThat(readFully(contentStore(Duration.ofSeconds(30), 2))).isEqualTo(CONTENT);
        var elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(elapsed).isLessThan(Duration.ofMillis(SLOW_RESPONSE_MILLIS / 2));
        assertThat(tcpProxy.requestHeads()).hasSize(2);
        assertThat(tcpProxy.awaitPeerClose(2, 10, TimeUnit.SECONDS))
                .as("the connection of the hedged request must be closed")
                .isTrue();
    }

    @Test
    void allRequestsSlow_operationTimesOut() throws Exception {
        tcpProxy.setFirstResponseDelayMillis(request -> SLOW_RESPONSE_MILLIS);
        var contentStore = contentStore(Duration.ofSeconds(1), 2);

        var start = System.nanoTime();
        assertThatThrownBy(() -> contentStore.getReader(OBJECT, null))
                .isInstanceOf(UnreadableContentException.class)
                .hasCauseInstanceOf(TimeoutException.class);
        var elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(elapsed)
                .as("the operation must give up at the operation timeout")
                .isLessThan(Duration.ofMillis(SLOW_RESPONSE_MILLIS / 2));
        assertThat(tcpProxy.requestHeads())
                .as("no more than the maximum number of attempts must be sent")
                .hasSize(2);
        assertThat(tcpProxy.awaitPeerClose(1, 10, TimeUnit.SECONDS)).isTrue();
        assertThat(tcpProxy.awaitPeerClose(2, 10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void missingObject_failsWithoutHedging() {
        var contentStore = contentStore();

        assertThatThrownBy(() -> contentStore.getReader(ContentReference.of("missing"), null))
                .isInstanceOf(UnreadableContentException.class)
                .hasCauseInstanceOf(NoSuchKeyException.class);

        assertThat(tcpProxy.requestHeads())
                .as("a request that fails within the attempt offset must not be hedged")
                .hasSize(1);
    }
}
