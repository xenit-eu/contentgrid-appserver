package com.contentgrid.appserver.autoconfigure.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contentgrid.appserver.autoconfigure.contentstore.FilesystemContentStoreAutoConfiguration;
import com.contentgrid.appserver.autoconfigure.s3.testing.S3LoopbackServer;
import com.contentgrid.appserver.autoconfigure.s3.testing.S3TestClients;
import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.UnreadableContentException;
import com.contentgrid.appserver.contentstore.api.UnwritableContentException;
import com.contentgrid.appserver.domain.content.ContentStoreResolver;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLoggingListener;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3AsyncClient;

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
                });
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
                });
    }

    @Configuration
    static class S3ClientConfiguration {

        @Bean
        S3AsyncClient testS3AsyncClient() {
            return S3TestClients.s3AsyncClient("http://localhost");
        }
    }

    @Test
    void checkS3_managedClients_shareTransportWithReadWriteSeparation() {
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
                    assertThat(context).hasSingleBean(S3ContentStoreClients.class);
                    var clients = context.getBean(S3ContentStoreClients.class);
                    assertThat(clients.readClient()).isNotNull();
                    assertThat(clients.writeClient()).isNotNull();
                    assertThat(clients.readClient()).isNotSameAs(clients.writeClient());
                    assertThat(clients.httpClient()).isNotNull();
                    // The historical s3AsyncClient bean stays available for external injectors and keeps
                    // its multipart-enabled write behavior; it is owned by the holder, not by Spring.
                    assertThat(context).hasSingleBean(S3AsyncClient.class);
                    assertThat(context.getBean(S3AsyncClient.class)).isSameAs(clients.writeClient());
                    assertThat(context).hasBean("s3ContentStoreResolver");
                });
    }

    @Test
    void checkS3_existingClient_createsNoManagedClientsOrTransport() {
        contextRunner
                .withUserConfiguration(S3ClientConfiguration.class)
                .withPropertyValues(
                        "contentgrid.appserver.content-store.type=s3",
                        "contentgrid.appserver.content.s3.bucket=fake"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(S3AsyncClient.class);
                    assertThat(context).doesNotHaveBean(S3ContentStoreClients.class);
                    assertThat(context).doesNotHaveBean("s3AsyncClient");
                    assertThat(context).hasBean("s3ContentStoreResolver");
                });
    }

    @Test
    void checkS3_managedClients_closedWithContext() {
        contextRunner
                .withPropertyValues(
                        "contentgrid.appserver.content-store.type=s3",
                        "contentgrid.appserver.content.s3.url=http://localhost",
                        "contentgrid.appserver.content.s3.accessKey=accessKey",
                        "contentgrid.appserver.content.s3.secretKey=secretKey",
                        "contentgrid.appserver.content.s3.bucket=fake"
                )
                .run(context -> {
                    var clients = context.getBean(S3ContentStoreClients.class);
                    assertThatNoException().isThrownBy(context::close);
                    // The context close already closed the holder; closing again stays a safe no-op.
                    // (Whether operations fail after close depends on JVM-global SDK transport sharing and
                    // is covered deterministically in S3ContentStoreClientsTest instead.)
                    assertThatNoException().isThrownBy(clients::close);
                });
    }

    @Test
    void checkS3_managedClients_roundTripThroughLoopback() throws Exception {
        var content = "loopback round-trip".getBytes(StandardCharsets.UTF_8);
        try (var server = S3LoopbackServer.start()) {
            contextRunner
                    .withPropertyValues(
                            "contentgrid.appserver.content-store.type=s3",
                            "contentgrid.appserver.content.s3.url=" + server.endpoint(),
                            "contentgrid.appserver.content.s3.accessKey=accessKey",
                            "contentgrid.appserver.content.s3.secretKey=secretKey",
                            "contentgrid.appserver.content.s3.bucket=fake"
                    )
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        // The S3 resolver ignores the application argument, a null is sufficient here
                        var store = context.getBean(ContentStoreResolver.class).resolve(null);
                        try {
                            var reference = store.writeContent(new ByteArrayInputStream(content))
                                    .getReference();
                            try (var stream = store.getReader(reference, null).getContentInputStream()) {
                                assertThat(stream.readAllBytes()).isEqualTo(content);
                            }
                            store.remove(reference);
                            assertThatThrownBy(
                                    () -> {
                                        try (var stream = store.getReader(ContentReference.of("missing"),
                                                null).getContentInputStream()) {
                                            stream.readAllBytes();
                                        }
                                    })
                                    .isInstanceOf(UnreadableContentException.class);
                        } catch (UnwritableContentException | IOException e) {
                            throw new AssertionError("round-trip through loopback S3 must succeed", e);
                        }
                        assertThat(server.requestsFor("PUT")).hasSize(1);
                        assertThat(server.requestsFor("GET")).hasSize(2);
                        assertThat(server.requestsFor("DELETE")).hasSize(1);
                        assertThat(server.requests())
                                .noneMatch(S3LoopbackServer.RecordedRequest::hasPartNumber);
                    });
        }
    }

}
