package com.contentgrid.appserver.autoconfigure.contentstore;

import com.contentgrid.appserver.autoconfigure.Bootstrap;
import com.contentgrid.appserver.autoconfigure.contentstore.EncryptedContentStoreAutoConfiguration.EncryptionEngineProperties;
import com.contentgrid.appserver.autoconfigure.contentstore.EncryptedContentStoreAutoConfiguration.EncryptionKeyWrapperProperties;
import com.contentgrid.appserver.contentstore.api.resolver.ContentStoreResolver;
import com.contentgrid.appserver.contentstore.impl.encryption.EncryptedContentStore;
import com.contentgrid.appserver.contentstore.impl.encryption.engine.AesCtrEncryptionEngine;
import com.contentgrid.appserver.contentstore.impl.encryption.engine.AlfrescoCompatibleEncryptionEngine;
import com.contentgrid.appserver.contentstore.impl.encryption.engine.ContentEncryptionEngine;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.DataEncryptionKeyAccessor;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.DataEncryptionKeyWrapper;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.TableStorageDataEncryptionKeyAccessor;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.UnencryptedSymmetricDataEncryptionKeyWrapper;

import java.util.List;
import java.util.Set;

import org.jooq.DSLContext;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@AutoConfiguration
@ConditionalOnClass(EncryptedContentStore.class)
@ConditionalOnBooleanProperty("contentgrid.appserver.content.encryption.enabled")
@EnableConfigurationProperties({EncryptionKeyWrapperProperties.class, EncryptionEngineProperties.class})
public class EncryptedContentStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DataEncryptionKeyAccessor.class)
    TableStorageDataEncryptionKeyAccessor tableStorageEncryptionKeyAccessor(DSLContext dslContext) {
        return new TableStorageDataEncryptionKeyAccessor(dslContext);
    }

    @Primary
    @Bean
    ContentStoreResolver encryptedContentStoreResolver(ContentStoreResolver contentStoreResolver, DataEncryptionKeyAccessor encryptionKeyAccessor,
            List<DataEncryptionKeyWrapper> encryptionKeyWrappers, List<ContentEncryptionEngine> encryptionEngines,
            EncryptionKeyWrapperProperties encryptionKeyWrapperAlgorithms, EncryptionEngineProperties encryptionEngineProperties) {
        if (encryptionKeyWrappers.isEmpty()) {
            encryptionKeyWrappers = encryptionKeyWrapperAlgorithms.algorithms().stream()
                    .map(this::dataEncryptionKeyWrapperForAlgorithm)
                    .toList();
        }
        if (encryptionEngines.isEmpty()) {
            encryptionEngines = encryptionEngineProperties.algorithms().stream()
                    .map(this::contentEncryptionEngineForAlgorithm)
                    .toList();
        }
        // Make lists final, so they can be used in lambda function
        var finalEncryptionKeyWrappers = encryptionKeyWrappers;
        var finalEncryptionEngines = encryptionEngines;
        return application -> new EncryptedContentStore(contentStoreResolver.resolve(application),
                encryptionKeyAccessor, finalEncryptionKeyWrappers, finalEncryptionEngines);
    }

    @Bean
    @ConditionalOnBean(TableStorageDataEncryptionKeyAccessor.class)
    TableInitializer dekTableInitializer(
            TableStorageDataEncryptionKeyAccessor encryptionKeyAccessor,
            @Value("${contentgrid.appserver.content.encryption.bootstrap-tables:NONE}") Bootstrap bootstrap
    ) {
        return new TableInitializer(encryptionKeyAccessor, bootstrap);
    }

    private DataEncryptionKeyWrapper dataEncryptionKeyWrapperForAlgorithm(EncryptionKeyWrapperAlgorithm algorithm) {
        return switch (algorithm) {
            case NONE -> new UnencryptedSymmetricDataEncryptionKeyWrapper(true);
        };
    }

    private ContentEncryptionEngine contentEncryptionEngineForAlgorithm(EncryptionEngineAlgorithm algorithm) {
        return switch (algorithm) {
            case AES128_CTR -> new AesCtrEncryptionEngine(128);
            case AES192_CTR -> new AesCtrEncryptionEngine(192);
            case AES256_CTR -> new AesCtrEncryptionEngine(256);
            case ALFRESCO -> new AlfrescoCompatibleEncryptionEngine();
        };
    }

    @ConfigurationProperties("contentgrid.appserver.content.encryption.wrapper")
    record EncryptionKeyWrapperProperties(
            @DefaultValue("NONE")
            Set<EncryptionKeyWrapperAlgorithm> algorithms
    ) {}

    @ConfigurationProperties("contentgrid.appserver.content.encryption.engine")
    record EncryptionEngineProperties(
            @DefaultValue("AES128_CTR")
            Set<EncryptionEngineAlgorithm> algorithms
    ) {}

    enum EncryptionKeyWrapperAlgorithm {
        NONE
    }

    enum EncryptionEngineAlgorithm {
        AES128_CTR,
        AES192_CTR,
        AES256_CTR,
        ALFRESCO
    }

    @lombok.Value
    static class TableInitializer implements InitializingBean, DisposableBean {

        TableStorageDataEncryptionKeyAccessor encryptionKeyAccessor;
        Bootstrap bootstrap;

        @Override
        public void afterPropertiesSet() throws Exception {
            if (bootstrap == Bootstrap.CREATE || bootstrap == Bootstrap.CREATE_DROP) {
                encryptionKeyAccessor.setupTables();
            }
        }

        @Override
        public void destroy() throws Exception {
            if (bootstrap == Bootstrap.CREATE_DROP) {
                encryptionKeyAccessor.dropTables();
            }
        }
    }
}
