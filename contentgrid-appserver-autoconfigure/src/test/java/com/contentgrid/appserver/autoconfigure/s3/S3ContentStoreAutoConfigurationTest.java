package com.contentgrid.appserver.autoconfigure.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.contentgrid.appserver.autoconfigure.contentstore.FilesystemContentStoreAutoConfiguration;
import com.contentgrid.appserver.autoconfigure.s3.testing.S3TestClients;
import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.impl.s3.S3ContentStore;
import com.contentgrid.appserver.contentstore.impl.s3.S3ReadRetryPolicy;
import com.contentgrid.appserver.domain.content.ContentStoreResolver;
import java.io.ByteArrayInputStream;
import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLoggingListener;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.internal.NettyConfiguration;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.utils.AttributeMap;

class S3ContentStoreAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            // Use initializer to have default conversion service
            .withInitializer(applicationContext -> applicationContext.getBeanFactory().setConversionService(new ApplicationConversionService()))
            .withInitializer(ConditionEvaluationReportLoggingListener.forLogLevel(LogLevel.INFO))
            .withConfiguration(AutoConfigurations.of(FilesystemContentStoreAutoConfiguration.class, S3ContentStoreAutoConfiguration.class));

    @Test
    void checkDefaults() {
        contextRunner
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ContentStoreResolver.class);
                });
    }

    @Test
    void checkS3_minimalValues() {
        contextRunner
                .withPropertyValues(
                        "contentgrid.appserver.content-store.type=s3",
                        "contentgrid.appserver.content.s3.url=http://localhost",
                        "contentgrid.appserver.content.s3.accessKey=accessKey",
                        "contentgrid.appserver.content.s3.secretKey=secretKey",
                        "contentgrid.appserver.content.s3.bucket=fake"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("s3ContentStoreResolver");
                });
    }

    @Test
    void checkS3_allValues() {
        contextRunner
                .withPropertyValues(
                        "contentgrid.appserver.content-store.type=s3",
                        "contentgrid.appserver.content.s3.url=http://localhost",
                        "contentgrid.appserver.content.s3.accessKey=accessKey",
                        "contentgrid.appserver.content.s3.secretKey=secretKey",
                        "contentgrid.appserver.content.s3.bucket=fake",
                        "contentgrid.appserver.content.s3.region=none",
                        "contentgrid.appserver.content.s3.path-style-access=false",
                        "contentgrid.appserver.content.s3.connection-pool-size=5",
                        "contentgrid.appserver.content.s3.connection-pool-keep-alive-seconds=30"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("s3ContentStoreResolver");
                    var properties = context.getBean(S3ContentStoreAutoConfiguration.S3Properties.class);
                    assertThat(properties.url()).isEqualTo("http://localhost");
                    assertThat(properties.accessKey()).isEqualTo("accessKey");
                    assertThat(properties.secretKey()).isEqualTo("secretKey");
                    assertThat(properties.bucket()).isEqualTo("fake");
                    assertThat(properties.region()).isEqualTo("none");
                    assertThat(properties.pathStyleAccess()).isFalse();
                    assertThat(properties.connectionPoolSize()).isEqualTo(5);
                    assertThat(properties.connectionPoolKeepAliveSeconds()).isEqualTo(30);
                });
    }

    @Test
    void checkS3_defaults() {
        contextRunner
                .withPropertyValues(
                        "contentgrid.appserver.content-store.type=s3",
                        "contentgrid.appserver.content.s3.url=http://localhost",
                        "contentgrid.appserver.content.s3.accessKey=accessKey",
                        "contentgrid.appserver.content.s3.secretKey=secretKey",
                        "contentgrid.appserver.content.s3.bucket=fake"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("s3ContentStoreResolver");
                    var properties = context.getBean(S3ContentStoreAutoConfiguration.S3Properties.class);
                    assertThat(properties.pathStyleAccess()).isTrue();
                    assertThat(properties.connectionPoolSize()).isEqualTo(0);
                    assertThat(properties.connectionPoolKeepAliveSeconds()).isEqualTo(1);
                    assertThat(properties.readRetry().maxRetries()).isEqualTo(S3ReadRetryPolicy.DEFAULT_MAX_RETRIES);
                    assertThat(properties.readRetry().acquisitionTimeout()).isEqualTo(S3ReadRetryPolicy.DEFAULT_ACQUISITION_TIMEOUT);
                    assertThat(properties.readRetry().minDelay()).isEqualTo(Duration.ZERO);
                    assertThat(properties.readRetry().maxDelay()).isEqualTo(Duration.ofMillis(250));
                    assertThat(readRetryPolicy(context.getBean(ContentStoreResolver.class)))
                            .isEqualTo(S3ReadRetryPolicy.DEFAULT);
                });
    }

    @Test
    void checkS3_customReadRetryValues() {
        contextRunner
                .withPropertyValues(
                        "contentgrid.appserver.content-store.type=s3",
                        "contentgrid.appserver.content.s3.url=http://localhost",
                        "contentgrid.appserver.content.s3.accessKey=accessKey",
                        "contentgrid.appserver.content.s3.secretKey=secretKey",
                        "contentgrid.appserver.content.s3.bucket=fake",
                        "contentgrid.appserver.content.s3.read-retry.max-retries=3",
                        "contentgrid.appserver.content.s3.read-retry.acquisition-timeout=2s",
                        "contentgrid.appserver.content.s3.read-retry.min-delay=400ms",
                        "contentgrid.appserver.content.s3.read-retry.max-delay=800ms"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var policy = readRetryPolicy(context.getBean(ContentStoreResolver.class));
                    assertThat(policy).isEqualTo(new S3ReadRetryPolicy(3, Duration.ofSeconds(2),
                            Duration.ofMillis(400), Duration.ofMillis(800)));
                });
    }

    @Test
    void checkS3_zeroReadRetries() {
        contextRunner
                .withPropertyValues(
                        "contentgrid.appserver.content-store.type=s3",
                        "contentgrid.appserver.content.s3.url=http://localhost",
                        "contentgrid.appserver.content.s3.accessKey=accessKey",
                        "contentgrid.appserver.content.s3.secretKey=secretKey",
                        "contentgrid.appserver.content.s3.bucket=fake",
                        "contentgrid.appserver.content.s3.read-retry.max-retries=0"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var policy = readRetryPolicy(context.getBean(ContentStoreResolver.class));
                    assertThat(policy.maxRetries()).isZero();
                    assertThat(policy.acquisitionTimeout()).isEqualTo(S3ReadRetryPolicy.DEFAULT_ACQUISITION_TIMEOUT);
                });
    }

    @Test
    void checkS3_rejectsNegativeReadRetries() {
        contextRunner
                .withPropertyValues(
                        "contentgrid.appserver.content-store.type=s3",
                        "contentgrid.appserver.content.s3.url=http://localhost",
                        "contentgrid.appserver.content.s3.accessKey=accessKey",
                        "contentgrid.appserver.content.s3.secretKey=secretKey",
                        "contentgrid.appserver.content.s3.bucket=fake",
                        "contentgrid.appserver.content.s3.read-retry.max-retries=-1"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void checkS3_rejectsZeroReadRetryAcquisitionTimeout() {
        contextRunner
                .withPropertyValues(
                        "contentgrid.appserver.content-store.type=s3",
                        "contentgrid.appserver.content.s3.url=http://localhost",
                        "contentgrid.appserver.content.s3.accessKey=accessKey",
                        "contentgrid.appserver.content.s3.secretKey=secretKey",
                        "contentgrid.appserver.content.s3.bucket=fake",
                        "contentgrid.appserver.content.s3.read-retry.acquisition-timeout=0s"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void checkS3_rejectsNegativeReadRetryAcquisitionTimeout() {
        contextRunner
                .withPropertyValues(
                        "contentgrid.appserver.content-store.type=s3",
                        "contentgrid.appserver.content.s3.url=http://localhost",
                        "contentgrid.appserver.content.s3.accessKey=accessKey",
                        "contentgrid.appserver.content.s3.secretKey=secretKey",
                        "contentgrid.appserver.content.s3.bucket=fake",
                        "contentgrid.appserver.content.s3.read-retry.acquisition-timeout=-1s"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    private WebApplicationContextRunner configuredS3Context() {
        return contextRunner.withPropertyValues(
                "contentgrid.appserver.content-store.type=s3",
                "contentgrid.appserver.content.s3.url=http://localhost",
                "contentgrid.appserver.content.s3.access-key=accessKey",
                "contentgrid.appserver.content.s3.secret-key=secretKey",
                "contentgrid.appserver.content.s3.bucket=fake");
    }

    @Test
    void httpTimeoutDefaults_preserveEffectiveNettySettings() {
        var configuration = captureHttpConfiguration(() -> configuredS3Context().run(context ->
                assertThat(context).hasNotFailed()));
        assertThat(configuration.connectTimeoutMillis()).isEqualTo(2000);
        assertThat(configuration.tlsHandshakeTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(configuration.connectionAcquireTimeoutMillis()).isEqualTo(10000);
        assertThat(configuration.readTimeoutMillis()).isEqualTo(30000);
        assertThat(configuration.writeTimeoutMillis()).isEqualTo(30000);
    }

    @Test
    void configuredHttpTimeouts_reachNettyIndependently() {
        var configuration = captureHttpConfiguration(() -> configuredS3Context().withPropertyValues(
                "contentgrid.appserver.content.s3.connection-timeout=7s",
                "contentgrid.appserver.content.s3.tls-negotiation-timeout=11s",
                "contentgrid.appserver.content.s3.connection-acquisition-timeout=13s",
                "contentgrid.appserver.content.s3.read-timeout=17s",
                "contentgrid.appserver.content.s3.write-timeout=19s")
                .run(context -> assertThat(context).hasNotFailed()));
        assertThat(configuration.connectTimeoutMillis()).isEqualTo(7000);
        assertThat(configuration.tlsHandshakeTimeout()).isEqualTo(Duration.ofSeconds(11));
        assertThat(configuration.connectionAcquireTimeoutMillis()).isEqualTo(13000);
        assertThat(configuration.readTimeoutMillis()).isEqualTo(17000);
        assertThat(configuration.writeTimeoutMillis()).isEqualTo(19000);
    }

    @Test
    void changingConnectionTimeout_preservesDefaultTlsTimeout() {
        var configuration = captureHttpConfiguration(() -> configuredS3Context().withPropertyValues(
                "contentgrid.appserver.content.s3.connection-timeout=10s")
                .run(context -> assertThat(context).hasNotFailed()));
        assertThat(configuration.connectTimeoutMillis()).isEqualTo(10000);
        assertThat(configuration.tlsHandshakeTimeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void zeroReadAndWriteTimeouts_disableSocketTimeouts() {
        var configuration = captureHttpConfiguration(() -> configuredS3Context().withPropertyValues(
                "contentgrid.appserver.content.s3.read-timeout=0ms",
                "contentgrid.appserver.content.s3.write-timeout=0ms")
                .run(context -> assertThat(context).hasNotFailed()));
        assertThat(configuration.readTimeoutMillis()).isZero();
        assertThat(configuration.writeTimeoutMillis()).isZero();
    }

    static Stream<String> invalidHttpTimeouts() {
        return Stream.of("connection-timeout=0ms", "connection-timeout=-1ms",
                "tls-negotiation-timeout=0ms", "tls-negotiation-timeout=-1ms",
                "connection-acquisition-timeout=0ms", "connection-acquisition-timeout=-1ms",
                "read-timeout=-1ms", "write-timeout=-1ms");
    }

    @ParameterizedTest
    @MethodSource("invalidHttpTimeouts")
    void invalidHttpTimeouts_rejected(String setting) {
        configuredS3Context().withPropertyValues("contentgrid.appserver.content.s3." + setting)
                .run(context -> assertThat(context).hasFailed());
    }

    static Stream<Arguments> invalidRetryDelays() {
        return Stream.of(
                Arguments.of((Object) new String[]{"min-delay=-1ms"}),
                Arguments.of((Object) new String[]{"max-delay=-1ms"}),
                Arguments.of((Object) new String[]{"min-delay=300ms", "max-delay=100ms"}));
    }

    @ParameterizedTest
    @MethodSource("invalidRetryDelays")
    void invalidRetryDelays_rejected(String[] settings) {
        var properties = Stream.of(settings)
                .map(setting -> "contentgrid.appserver.content.s3.read-retry." + setting)
                .toArray(String[]::new);
        configuredS3Context().withPropertyValues(properties).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void zeroMaxRetryDelay_allowImmediateRecoveryWithDefaultMinimum() {
        var client = mock(S3AsyncClient.class);
        var content = new byte[]{42};
        var response = new ResponseInputStream<>(GetObjectResponse.builder().contentLength(1L).build(),
                AbortableInputStream.create(new ByteArrayInputStream(content)));
        doReturn(CompletableFuture.failedFuture(SdkClientException.create("connect failed", new ConnectException())),
                CompletableFuture.completedFuture(response)).when(client)
                .getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));

        configuredS3Context().withBean(S3AsyncClient.class, () -> client).withPropertyValues(
                "contentgrid.appserver.content.s3.read-retry.max-delay=0ms",
                "contentgrid.appserver.content.s3.read-retry.acquisition-timeout=80ms")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var store = context.getBean(ContentStoreResolver.class).resolve(null);
                    try (var input = store.getReader(ContentReference.of("test-object"), null).getContentInputStream()) {
                        assertThat(input.readAllBytes()).containsExactly(content);
                    }
                    verify(client, times(2)).getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));
                });
    }

    // Inspect the real built Netty client, not just property binding or mocked setter invocations.
    private static NettyConfiguration captureHttpConfiguration(Runnable action) {
        var builder = spy(NettyNioAsyncHttpClient.builder());
        var configuration = new AtomicReference<NettyConfiguration>();
        doAnswer(invocation -> {
            var httpClient = (NettyNioAsyncHttpClient) invocation.callRealMethod();
            configuration.set((NettyConfiguration) ReflectionTestUtils.getField(httpClient, "configuration"));
            return httpClient;
        }).when(builder).buildWithDefaults(any(AttributeMap.class));
        try (var builders = mockStatic(NettyNioAsyncHttpClient.class)) {
            builders.when(NettyNioAsyncHttpClient::builder).thenReturn(builder);
            action.run();
        }
        assertThat(configuration.get()).isNotNull();
        return configuration.get();
    }

    @Test
    void checkS3_missingUrl() {
        contextRunner
                .withPropertyValues(
                        "contentgrid.appserver.content-store.type=s3",
                        "contentgrid.appserver.content.s3.accessKey=accessKey",
                        "contentgrid.appserver.content.s3.secretKey=secretKey",
                        "contentgrid.appserver.content.s3.bucket=fake"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                });
    }

    @Test
    void checkS3_missingCredentials() {
        contextRunner
                .withPropertyValues(
                        "contentgrid.appserver.content-store.type=s3",
                        "contentgrid.appserver.content.s3.url=http://localhost",
                        "contentgrid.appserver.content.s3.bucket=fake"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                });
    }

    @Test
    void checkS3_missingBucket() {
        contextRunner
                .withPropertyValues(
                        "contentgrid.appserver.content-store.type=s3",
                        "contentgrid.appserver.content.s3.url=http://localhost",
                        "contentgrid.appserver.content.s3.accessKey=accessKey",
                        "contentgrid.appserver.content.s3.secretKey=secretKey"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                });
    }

    @Test
    void checkS3_existingClient() {
        contextRunner
                .withUserConfiguration(S3ClientConfiguration.class)
                .withPropertyValues(
                        "contentgrid.appserver.content-store.type=s3",
                        "contentgrid.appserver.content.s3.bucket=fake"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("s3ContentStoreResolver");
                    assertThat(context).doesNotHaveBean("s3AsyncClient");
                    var contentStore = (S3ContentStore) context.getBean(ContentStoreResolver.class).resolve(null);
                    assertThat(ReflectionTestUtils.getField(contentStore, "client"))
                            .isSameAs(context.getBean("testS3AsyncClient"));
                    assertThat(readRetryPolicy(context.getBean(ContentStoreResolver.class)))
                            .isEqualTo(S3ReadRetryPolicy.DEFAULT);
                });
    }

    private static S3ReadRetryPolicy readRetryPolicy(ContentStoreResolver resolver) {
        var contentStore = (S3ContentStore) resolver.resolve(null);
        return (S3ReadRetryPolicy) ReflectionTestUtils.getField(contentStore, "readRetryPolicy");
    }

    @Configuration
    static class S3ClientConfiguration {

        @Bean
        S3AsyncClient testS3AsyncClient() {
            return S3TestClients.s3AsyncClient("http://localhost");
        }
    }

}
