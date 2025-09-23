package com.contentgrid.appserver.autoconfigure.contentstore;

import com.contentgrid.appserver.autoconfigure.contentstore.EncryptedContentStoreAutoConfiguration.EncryptionEngineProperties;
import com.contentgrid.appserver.autoconfigure.contentstore.EncryptedContentStoreAutoConfiguration.EncryptionKeyWrapperProperties;
import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.contentstore.impl.encryption.EncryptedContentStore;
import com.contentgrid.appserver.contentstore.impl.encryption.engine.AesCtrEncryptionEngine;
import com.contentgrid.appserver.contentstore.impl.encryption.engine.ContentEncryptionEngine;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.DataEncryptionKeyAccessor;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.DataEncryptionKeyWrapper;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.TableStorageDataEncryptionKeyAccessor;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.UnencryptedSymmetricDataEncryptionKeyWrapper;
import java.util.List;
import java.util.Set;
import lombok.NonNull;
import org.jooq.DSLContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jooq.JooqAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@AutoConfiguration(after = {FileSystemContentStoreAutoConfiguration.class, S3ContentStoreAutoConfiguration.class, JooqAutoConfiguration.class})
@ConditionalOnClass(EncryptedContentStore.class)
@ConditionalOnBean(ContentStore.class)
@ConditionalOnBooleanProperty("contentgrid.appserver.content.encryption.enabled")
@EnableConfigurationProperties({EncryptionKeyWrapperProperties.class, EncryptionEngineProperties.class})
public class EncryptedContentStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(DSLContext.class)
    DataEncryptionKeyAccessor tableStorageEncryptionKeyAccessor(DSLContext dslContext) {
        return new TableStorageDataEncryptionKeyAccessor(dslContext);
    }

    @Primary
    @Bean
    ContentStore encryptedContentStore(ContentStore contentStore, DataEncryptionKeyAccessor encryptionKeyAccessor,
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
        return new EncryptedContentStore(contentStore, encryptionKeyAccessor, encryptionKeyWrappers, encryptionEngines);
    }

    private DataEncryptionKeyWrapper dataEncryptionKeyWrapperForAlgorithm(String algorithm) {
        return switch (algorithm.toLowerCase()) {
            case "none" -> new UnencryptedSymmetricDataEncryptionKeyWrapper(true);
            default -> throw new UnsupportedOperationException("Data encryption key wrapper algorithm %s not supported".formatted(algorithm));
        };
    }

    private ContentEncryptionEngine contentEncryptionEngineForAlgorithm(EncryptionEngineAlgorithmProperties properties) {
        return switch (properties.type().toLowerCase()) {
            case "aes-ctr" -> new AesCtrEncryptionEngine(properties.keySizeBits());
            default -> throw new UnsupportedOperationException("Content encryption algorithm %s not supported".formatted(properties.type()));
        };
    }

    @ConfigurationProperties("contentgrid.appserver.content.encryption.wrapper")
    record EncryptionKeyWrapperProperties(
            @DefaultValue("none")
            Set<String> algorithms
    ) {}

    @ConfigurationProperties("contentgrid.appserver.content.encryption.engine")
    record EncryptionEngineProperties(
            Set<EncryptionEngineAlgorithmProperties> algorithms
    ) {
        public Set<EncryptionEngineAlgorithmProperties> algorithms() {
            if (algorithms == null || algorithms.isEmpty()) {
                // return default value
                return Set.of(new EncryptionEngineAlgorithmProperties("AES-CTR", 128));
            } else {
                return algorithms;
            }
        }
    }

    record EncryptionEngineAlgorithmProperties(
            @NonNull
            String type,
            @DefaultValue("128")
            int keySizeBits
    ) {}
}
