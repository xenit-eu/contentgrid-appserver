package com.contentgrid.appserver.autoconfigure.contentstore;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.contentstore.impl.encryption.EncryptedContentStore;
import com.contentgrid.appserver.contentstore.impl.encryption.engine.ContentEncryptionEngine;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.DataEncryptionKeyAccessor;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.DataEncryptionKeyWrapper;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.TableStorageDataEncryptionKeyAccessor;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.UnencryptedSymmetricDataEncryptionKeyWrapper;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.WrappingKeyId;
import com.contentgrid.appserver.contentstore.impl.encryption.testing.InMemoryDataEncryptionKeyAccessor;
import com.contentgrid.appserver.contentstore.impl.encryption.testing.XorTestEncryptionEngine;
import com.contentgrid.appserver.contentstore.impl.fs.FilesystemContentStore;
import java.util.Set;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class EncryptedContentStoreAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            // Use initializer to have default conversion service
            .withInitializer(applicationContext -> applicationContext.getBeanFactory().setConversionService(new ApplicationConversionService()))
            .withInitializer(ConditionEvaluationReportLoggingListener.forLogLevel(LogLevel.INFO))
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,
                    TransactionAutoConfiguration.class, JooqAutoConfiguration.class,
                    FilesystemContentStoreAutoConfiguration.class, EncryptedContentStoreAutoConfiguration.class))
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
                    assertThat(context).hasFailed();
                });
    }

    @Test
    void checkWithMissingContentStore() {
        // no ContentStore delegate
        contextRunner
                .withClassLoader(new FilteredClassLoader(FilesystemContentStore.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(EncryptedContentStore.class);
                    assertThat(context).doesNotHaveBean(TableStorageDataEncryptionKeyAccessor.class);
                });
    }

    @Test
    void checkWithValidWrapperAlgorithm() {
        contextRunner
                .withPropertyValues("contentgrid.appserver.content.encryption.wrapper.algorithms[0]=none")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(EncryptedContentStore.class);
                });
    }

    @Test
    void checkWithInvalidWrapperAlgorithm() {
        contextRunner
                .withPropertyValues("contentgrid.appserver.content.encryption.wrapper.algorithms[0]=unknown")
                .run(context -> {
                    assertThat(context).hasFailed();
                });
    }

    @ParameterizedTest
    @CsvSource({"192", "256"})
    void checkWithValidEncryptionEngineKeySizeBits(int keySizeBits) {
        contextRunner
                .withPropertyValues(
                        "contentgrid.appserver.content.encryption.engine.algorithms[0]=AES" + keySizeBits + "-CTR"
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
                        "contentgrid.appserver.content.encryption.engine.algorithms[0]=AES" + keySizeBits + "-CTR"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                });
    }

    @Test
    void checkWithMultipleEncryptionKeyAlgorithms() {
        contextRunner
                .withPropertyValues(
                        "contentgrid.appserver.content.encryption.engine.algorithms[0]=AES128-CTR",
                        "contentgrid.appserver.content.encryption.engine.algorithms[1]=AES192-CTR",
                        "contentgrid.appserver.content.encryption.engine.algorithms[2]=AES256-CTR"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(EncryptedContentStore.class);
                });
    }

    @Test
    void checkWithUnknownEncryptionEngineKeyAlgorithm() {
        contextRunner
                .withPropertyValues("contentgrid.appserver.content.encryption.engine.algorithms[0]=unknown")
                .run(context -> {
                    assertThat(context).hasFailed();
                });
    }

    @Test
    void checkWithCustomEncryptionKeyWrapper() {
        contextRunner
                .withUserConfiguration(CustomEncryptionKeyWrapperConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(EncryptedContentStore.class);
                });
    }

    @Test
    void checkWithCustomEncryptionEngine() {
        contextRunner
                .withUserConfiguration(CustomEncryptionEngineConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(EncryptedContentStore.class);
                });
    }

    @Test
    void checkWithCustomEncryptionKeyAccessor() {
        contextRunner
                .withUserConfiguration(CustomEncryptionKeyAccessorConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(EncryptedContentStore.class);
                    assertThat(context).doesNotHaveBean(TableStorageDataEncryptionKeyAccessor.class);
                });
    }

    @Configuration
    static class CustomEncryptionKeyWrapperConfiguration {

        @Bean
        DataEncryptionKeyWrapper customEncryptionKeyWrapper() {
            return new UnencryptedSymmetricDataEncryptionKeyWrapper(true) {
                @Override
                public Set<WrappingKeyId> getSupportedKeyIds() {
                    return Set.of(WrappingKeyId.of("test"));
                }
            };
        }
    }

    @Configuration
    static class CustomEncryptionEngineConfiguration {

        @Bean
        ContentEncryptionEngine customEncryptionEngine() {
            return new XorTestEncryptionEngine();
        }
    }

    @Configuration
    static class CustomEncryptionKeyAccessorConfiguration {

        @Bean
        DataEncryptionKeyAccessor customEncryptionKeyAccessor() {
            return new InMemoryDataEncryptionKeyAccessor();
        }
    }

}