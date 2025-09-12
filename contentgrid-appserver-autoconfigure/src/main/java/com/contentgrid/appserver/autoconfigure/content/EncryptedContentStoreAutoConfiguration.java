package com.contentgrid.appserver.autoconfigure.content;

import com.contentgrid.appserver.content.api.ContentStore;
import com.contentgrid.appserver.content.impl.encryption.EncryptedContentStore;
import com.contentgrid.appserver.content.impl.encryption.engine.AesCtrEncryptionEngine;
import com.contentgrid.appserver.content.impl.encryption.engine.ContentEncryptionEngine;
import com.contentgrid.appserver.content.impl.encryption.keys.DataEncryptionKeyAccessor;
import com.contentgrid.appserver.content.impl.encryption.keys.DataEncryptionKeyWrapper;
import com.contentgrid.appserver.content.impl.encryption.keys.TableStorageDataEncryptionKeyAccessor;
import com.contentgrid.appserver.content.impl.encryption.keys.UnencryptedSymmetricDataEncryptionKeyWrapper;
import java.util.List;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jooq.JooqAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@AutoConfiguration(after = {FileSystemContentStoreAutoConfiguration.class, S3ContentStoreAutoConfiguration.class, JooqAutoConfiguration.class})
@ConditionalOnClass(EncryptedContentStore.class)
@ConditionalOnBooleanProperty("contentgrid.appserver.content.encryption.enabled")
public class EncryptedContentStoreAutoConfiguration {

    @Bean
    @ConditionalOnBean(DSLContext.class)
    DataEncryptionKeyAccessor tableStorageEncryptionKeyAccessor(DSLContext dslContext) {
        return new TableStorageDataEncryptionKeyAccessor(dslContext);
    }

    @Bean
    @ConditionalOnProperty(value = "contentgrid.appserver.content.encryption.wrapper.algorithm", havingValue = "none", matchIfMissing = true)
    DataEncryptionKeyWrapper unencryptedSymmetricDataEncryptionKeyWrapper() {
        return new UnencryptedSymmetricDataEncryptionKeyWrapper(true);
    }

    @Bean
    @ConditionalOnProperty(value = "contentgrid.appserver.content.encryption.engine.algorithm", havingValue = "AES-CTR", matchIfMissing = true)
    ContentEncryptionEngine aesCtrEncryptionEngine(@Value("${contentgrid.appserver.content.encryption.engine.key-size-bits:128}") int keySizeBits) {
        return new AesCtrEncryptionEngine(keySizeBits);
    }

    @Primary
    @Bean
    @ConditionalOnBean({ContentStore.class, DataEncryptionKeyAccessor.class, DataEncryptionKeyWrapper.class, ContentEncryptionEngine.class})
    ContentStore encryptedContentStore(ContentStore contentStore, DataEncryptionKeyAccessor encryptionKeyAccessor,
            List<DataEncryptionKeyWrapper> encryptionKeyWrappers, List<ContentEncryptionEngine> encryptionEngines) {
        return new EncryptedContentStore(contentStore, encryptionKeyAccessor, encryptionKeyWrappers, encryptionEngines);
    }
}
