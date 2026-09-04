package com.contentgrid.appserver.autoconfigure.contentstore;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.autoconfigure.contentstore.EncryptedContentStoreAutoConfiguration.EncryptionEngineAlgorithm;
import com.contentgrid.appserver.autoconfigure.contentstore.EncryptedContentStoreAutoConfiguration.EncryptionEngineProperties;
import com.contentgrid.appserver.autoconfigure.contentstore.EncryptedContentStoreAutoConfiguration.EncryptionKeyWrapperAlgorithm;
import com.contentgrid.appserver.autoconfigure.contentstore.EncryptedContentStoreAutoConfiguration.EncryptionKeyWrapperProperties;
import com.contentgrid.appserver.autoconfigure.query.engine.JOOQQueryEngineAutoConfiguration;
import com.contentgrid.appserver.contentstore.impl.fs.FilesystemContentStore;

import com.contentgrid.appserver.query.engine.jooq.JOOQQueryEngine;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.jooq.autoconfigure.JooqAutoConfiguration;
import org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLoggingListener;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
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
                    TransactionAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
                    JooqAutoConfiguration.class, JOOQQueryEngineAutoConfiguration.class,
                    FilesystemContentStoreAutoConfiguration.class, EncryptedContentStoreAutoConfiguration.class))
            .withPropertyValues(
                    "spring.datasource.url=jdbc:tc:postgresql:15:///",
                    "contentgrid.appserver.content-store.type=ephemeral"
            );

    @Test
    void checkDefaults() {
        contextRunner
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(EncryptedContentStoreResolver.class);
                    assertThat(context).hasSingleBean(DataEncryptionKeyTableCreator.class);
                    assertThat(context.getBean(EncryptionKeyWrapperProperties.class).algorithms())
                            .containsExactly(EncryptionKeyWrapperAlgorithm.NONE);
                    assertThat(context.getBean(EncryptionEngineProperties.class).algorithms())
                            .containsExactly(EncryptionEngineAlgorithm.AES128_CTR);
                });
    }

    @Test
    void checkConfiguredAlgorithms() {
        contextRunner
                .withPropertyValues(
                        "contentgrid.appserver.content.encryption.wrapper.algorithms[0]=NONE",
                        "contentgrid.appserver.content.encryption.engine.algorithms[0]=AES256-CTR",
                        "contentgrid.appserver.content.encryption.engine.algorithms[1]=ALFRESCO"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(EncryptionKeyWrapperProperties.class).algorithms())
                            .containsExactly(EncryptionKeyWrapperAlgorithm.NONE);
                    assertThat(context.getBean(EncryptionEngineProperties.class).algorithms())
                            .containsExactly(EncryptionEngineAlgorithm.AES256_CTR, EncryptionEngineAlgorithm.ALFRESCO);
                });
    }

    @Test
    void checkUnknownWrapperAlgorithm() {
        contextRunner
                .withPropertyValues(
                        "contentgrid.appserver.content.encryption.wrapper.algorithms[0]=UNKNOWN"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                });
    }

    @Test
    void checkUnknownEngineAlgorithm() {
        contextRunner
                .withPropertyValues(
                        "contentgrid.appserver.content.encryption.engine.algorithms[0]=UNKNOWN"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                });
    }

    @Test
    void checkAlfrescoEngineAlgorithmFirst() {
        // ALFRESCO is the only algorithm that can not encrypt, so it can not be used as first algorithm
        contextRunner
                .withPropertyValues(
                        "contentgrid.appserver.content.encryption.engine.algorithms[0]=ALFRESCO",
                        "contentgrid.appserver.content.encryption.engine.algorithms[1]=AES128-CTR"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                });
    }

    @Test
    void checkWithoutDSLContextResolver() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(DSLContextResolver.class, JOOQQueryEngine.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(EncryptedContentStoreResolver.class);
                });
    }

    @Test
    void checkWithMissingContentStore() {
        // no ContentStoreResolver delegate
        contextRunner
                .withClassLoader(new FilteredClassLoader(FilesystemContentStore.class))
                .run(context -> {
                    assertThat(context).hasFailed();
                });
    }
}