package com.contentgrid.appserver.autoconfigure.contentstore;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.contentstore.impl.s3.S3ContentStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLoggingListener;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

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
                    assertThat(context).doesNotHaveBean(S3ContentStore.class);
                    assertThat(context).hasBean("ephemeralContentStore");
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
                    assertThat(context).hasSingleBean(S3ContentStore.class);
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
                        "contentgrid.appserver.content.s3.region=none"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(S3ContentStore.class);
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
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ContentStore.class);
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
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ContentStore.class);
                });
    }

}