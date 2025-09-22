package com.contentgrid.appserver.autoconfigure.contentstore;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.contentstore.impl.encryption.EncryptedContentStore;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jooq.JooqAutoConfiguration;
import org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLoggingListener;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class EncryptedContentStoreAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            // Use initializer to have default conversion service
            .withInitializer(applicationContext -> applicationContext.getBeanFactory().setConversionService(new ApplicationConversionService()))
            .withInitializer(ConditionEvaluationReportLoggingListener.forLogLevel(LogLevel.INFO))
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,
                    TransactionAutoConfiguration.class, JooqAutoConfiguration.class,
                    FileSystemContentStoreAutoConfiguration.class, EncryptedContentStoreAutoConfiguration.class))
            .withPropertyValues(
                    "spring.datasource.url=jdbc:tc:postgresql:15:///",
                    "contentgrid.appserver.content.encryption.enabled=true"
            );

    @Test
    void checkDefaults() {
        contextRunner
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(EncryptedContentStore.class);
                });
    }

    @Test
    void checkWithoutDSLContext() {
        // no TableStorageDataEncryptionKeyAccessor
        contextRunner
                .withClassLoader(new FilteredClassLoader(DSLContext.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(EncryptedContentStore.class);
                });
    }

    @Test
    void checkWithValidWrapperAlgorithm() {
        contextRunner
                .withPropertyValues("contentgrid.appserver.content.encryption.wrapper.algorithm=none")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(EncryptedContentStore.class);
                });
    }

    @Test
    void checkWithInvalidWrapperAlgorithm() {
        contextRunner
                .withPropertyValues("contentgrid.appserver.content.encryption.wrapper.algorithm=unknown")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(EncryptedContentStore.class);
                });
    }

    @ParameterizedTest
    @CsvSource({"192", "256"})
    void checkWithValidEncryptionEngineKeySizeBits(int keySizeBits) {
        contextRunner
                .withPropertyValues(
                        "contentgrid.appserver.content.encryption.engine.algorithm=AES-CTR",
                        "contentgrid.appserver.content.encryption.engine.key-size-bits=" + keySizeBits
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(EncryptedContentStore.class);
                });
    }

    @ParameterizedTest
    @CsvSource({"64", "160"})
    void checkWithInvalidEncryptionEngineKeySizeBits(int keySizeBits) {
        contextRunner
                .withPropertyValues(
                        "contentgrid.appserver.content.encryption.engine.algorithm=AES-CTR",
                        "contentgrid.appserver.content.encryption.engine.key-size-bits=" + keySizeBits
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                });
    }

    @Test
    void checkWithUnknownEncryptionEngineKeyAlgorithm() {
        contextRunner
                .withPropertyValues("contentgrid.appserver.content.encryption.engine.algorithm=unknown")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(EncryptedContentStore.class);
                });
    }

}