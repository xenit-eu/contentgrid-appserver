package com.contentgrid.appserver.autoconfigure.contentstore;

import com.contentgrid.appserver.autoconfigure.contentstore.EncryptedContentStoreAutoConfiguration.EncryptionEngineProperties;
import com.contentgrid.appserver.autoconfigure.contentstore.EncryptedContentStoreAutoConfiguration.EncryptionKeyWrapperProperties;
import com.contentgrid.appserver.contentstore.impl.encryption.EncryptedContentStore;
import com.contentgrid.appserver.contentstore.impl.encryption.engine.AesCtrEncryptionEngine;
import com.contentgrid.appserver.contentstore.impl.encryption.engine.AlfrescoCompatibleEncryptionEngine;
import com.contentgrid.appserver.contentstore.impl.encryption.engine.ContentEncryptionEngine;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.DataEncryptionKeyWrapper;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.UnencryptedSymmetricDataEncryptionKeyWrapper;
import com.contentgrid.appserver.domain.content.ContentStoreResolver;

import com.contentgrid.appserver.query.engine.api.TableCreator;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;

import java.util.Set;

import org.jooq.DSLContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@AutoConfiguration
@ConditionalOnClass({EncryptedContentStore.class, DSLContext.class, DSLContextResolver.class})
@EnableConfigurationProperties({EncryptionKeyWrapperProperties.class, EncryptionEngineProperties.class})
public class EncryptedContentStoreAutoConfiguration {

    @Primary
    @Bean
    ContentStoreResolver encryptedContentStoreResolver(ContentStoreResolver contentStoreResolver,
            DSLContextResolver dslContextResolver, EncryptionKeyWrapperProperties keyWrapperProperties,
            EncryptionEngineProperties engineProperties) {
        var encryptionKeyWrappers = keyWrapperProperties.algorithms().stream()
                .map(EncryptedContentStoreAutoConfiguration::dataEncryptionKeyWrapperForAlgorithm)
                .toList();
        var encryptionEngines = engineProperties.algorithms().stream()
                .map(EncryptedContentStoreAutoConfiguration::contentEncryptionEngineForAlgorithm)
                .toList();
        return new EncryptedContentStoreResolver(contentStoreResolver, dslContextResolver, encryptionKeyWrappers,
                encryptionEngines);
    }

    @Bean
    TableCreator dataEncryptionKeyTableCreator(DSLContextResolver dslContextResolver) {
        return new DataEncryptionKeyTableCreator(dslContextResolver);
    }

    private static DataEncryptionKeyWrapper dataEncryptionKeyWrapperForAlgorithm(EncryptionKeyWrapperAlgorithm algorithm) {
        return switch (algorithm) {
            case NONE -> new UnencryptedSymmetricDataEncryptionKeyWrapper(true);
        };
    }

    private static ContentEncryptionEngine contentEncryptionEngineForAlgorithm(EncryptionEngineAlgorithm algorithm) {
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
    ) {
        EncryptionKeyWrapperProperties {
            if (algorithms.isEmpty()) {
                throw new IllegalArgumentException("At least one content encryption key wrapper algorithm must be configured.");
            }
        }
    }

    @ConfigurationProperties("contentgrid.appserver.content.encryption.engine")
    record EncryptionEngineProperties(
            @DefaultValue("AES128_CTR")
            Set<EncryptionEngineAlgorithm> algorithms
    ) {
        EncryptionEngineProperties {
            if (algorithms.isEmpty()) {
                throw new IllegalArgumentException("At least one content encryption engine algorithm must be configured.");
            }
            // Only the first algorithm is used for encryption, all algorithms are used for decryption.
            if (algorithms.iterator().next() == EncryptionEngineAlgorithm.ALFRESCO) {
                throw new IllegalArgumentException("Content encryption algorithm ALFRESCO can't be used for encryption, a different algorithm must be used as first element in the list.");
            }
        }
    }

    enum EncryptionKeyWrapperAlgorithm {
        NONE
    }

    enum EncryptionEngineAlgorithm {
        AES128_CTR,
        AES192_CTR,
        AES256_CTR,
        ALFRESCO
    }
}
