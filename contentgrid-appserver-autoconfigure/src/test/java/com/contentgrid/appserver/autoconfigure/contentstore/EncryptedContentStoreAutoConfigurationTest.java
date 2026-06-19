package com.contentgrid.appserver.autoconfigure.contentstore;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.autoconfigure.Bootstrap;
import com.contentgrid.appserver.autoconfigure.contentstore.EncryptedContentStoreAutoConfiguration.TableInitializer;
import com.contentgrid.appserver.autoconfigure.query.engine.JOOQQueryEngineAutoConfiguration;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.DataEncryptionKeyAccessor;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.TableStorageDataEncryptionKeyAccessor;
import com.contentgrid.appserver.contentstore.impl.encryption.resolver.EncryptedContentStoreResolver;
import com.contentgrid.appserver.contentstore.impl.encryption.testing.InMemoryDataEncryptionKeyAccessor;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
                    "contentgrid.appserver.content-store.type=ephemeral",
                    "contentgrid.appserver.content.encryption.enabled=true"
            );

    @Test
    void checkDefaults() {
        contextRunner
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(EncryptedContentStoreResolver.class);
                    assertThat(context).hasSingleBean(TableInitializer.class);
                    assertThat(context.getBean(TableInitializer.class).getBootstrap()).isEqualTo(Bootstrap.NONE);
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

    // TODO: remove next tests

    @Test
    void checkWithBootStrapTables_none() {
        contextRunner
                .withPropertyValues(
                        "contentgrid.appserver.content.encryption.enabled=true",
                        "contentgrid.appserver.content.encryption.bootstrap-tables=none"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(EncryptedContentStoreResolver.class);
                    assertThat(context).hasSingleBean(TableStorageDataEncryptionKeyAccessor.class);
                    assertThat(context).hasSingleBean(TableInitializer.class);
                    assertThat(context.getBean(TableInitializer.class).getBootstrap()).isEqualTo(Bootstrap.NONE);
                });
    }

    @Test
    void checkWithBootStrapTables_create() {
        contextRunner
                .withPropertyValues(
                        "contentgrid.appserver.content.encryption.enabled=true",
                        "contentgrid.appserver.content.encryption.bootstrap-tables=create"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(EncryptedContentStoreResolver.class);
                    assertThat(context).hasSingleBean(TableStorageDataEncryptionKeyAccessor.class);
                    assertThat(context).hasSingleBean(TableInitializer.class);
                    assertThat(context.getBean(TableInitializer.class).getBootstrap()).isEqualTo(Bootstrap.CREATE);
                });
    }

    @Test
    void checkWithBootStrapTables_createDrop() {
        contextRunner
                .withPropertyValues(
                        "contentgrid.appserver.content.encryption.enabled=true",
                        "contentgrid.appserver.content.encryption.bootstrap-tables=create-drop"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(EncryptedContentStoreResolver.class);
                    assertThat(context).hasSingleBean(TableStorageDataEncryptionKeyAccessor.class);
                    assertThat(context).hasSingleBean(TableInitializer.class);
                    assertThat(context.getBean(TableInitializer.class).getBootstrap()).isEqualTo(Bootstrap.CREATE_DROP);
                });
    }

    @Test
    void checkWithBootStrapTables_invalid() {
        contextRunner
                .withPropertyValues("contentgrid.appserver.content.encryption.bootstrap-tables=invalid")
                .run(context -> {
                    assertThat(context).hasFailed();
                });
    }

    @Test
    void checkWithCustomEncryptionKeyAccessorAndBootStrapTables() {
        contextRunner
                .withUserConfiguration(CustomEncryptionKeyAccessorConfiguration.class)
                .withPropertyValues(
                        "contentgrid.appserver.content.encryption.enabled=true",
                        "contentgrid.appserver.content.encryption.bootstrap-tables=create"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(EncryptedContentStoreResolver.class);
                    assertThat(context).doesNotHaveBean(TableStorageDataEncryptionKeyAccessor.class);
                    assertThat(context).doesNotHaveBean(TableInitializer.class);
                });
    }

    @Configuration
    static class CustomEncryptionKeyAccessorConfiguration {

        @Bean
        DataEncryptionKeyAccessor customEncryptionKeyAccessor() {
            return new InMemoryDataEncryptionKeyAccessor();
        }
    }

}