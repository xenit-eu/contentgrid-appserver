package com.contentgrid.appserver.autoconfigure.contentstore;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.domain.spi.contentstore.resolver.ContentStoreResolver;
import io.minio.MinioAsyncClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLoggingListener;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
                    assertThat(properties.connectionPoolSize()).isEqualTo(5);
                    assertThat(properties.connectionPoolKeepAliveSeconds()).isEqualTo(30);
                });
    }

    @Test
    void checkS3_defaultConnectionPoolSettings() {
        contextRunner
                .withPropertyValues(
                        "contentgrid.appserver.content-store.type=s3",
                        "contentgrid.appserver.content.s3.url=http://localhost",
                        "contentgrid.appserver.content.s3.bucket=fake"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("s3ContentStoreResolver");
                    var properties = context.getBean(S3ContentStoreAutoConfiguration.S3Properties.class);
                    assertThat(properties.connectionPoolSize()).isEqualTo(0);
                    assertThat(properties.connectionPoolKeepAliveSeconds()).isEqualTo(1);
                });
    }

    @Test
    void checkS3_missingUrl() {
        contextRunner
                .withPropertyValues(
                        "contentgrid.appserver.content-store.type=s3",
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
                        "contentgrid.appserver.content.s3.url=http://localhost"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                });
    }

    @Test
    void checkS3_existingMinioClient() {
        contextRunner
                .withUserConfiguration(MinioClientConfiguration.class)
                .withPropertyValues(
                        "contentgrid.appserver.content-store.type=s3",
                        "contentgrid.appserver.content.s3.bucket=fake"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("s3ContentStoreResolver");
                    assertThat(context).doesNotHaveBean("s3MinioAsyncClient");
                });
    }

    @Configuration
    static class MinioClientConfiguration {

        @Bean
        MinioAsyncClient testMinioAsyncClient() {
            return MinioAsyncClient.builder()
                    .endpoint("http://localhost")
                    .credentials("foo", "bar")
                    .build();
        }
    }

}