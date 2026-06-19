package com.contentgrid.appserver.autoconfigure.contentstore;

import com.contentgrid.appserver.autoconfigure.Bootstrap;
import com.contentgrid.appserver.contentstore.api.resolver.ContentStoreResolver;
import com.contentgrid.appserver.contentstore.impl.encryption.EncryptedContentStore;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.DataEncryptionKeyAccessor;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.TableStorageDataEncryptionKeyAccessor;

import com.contentgrid.appserver.contentstore.impl.encryption.resolver.EncryptedContentStoreResolver;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;

import org.jooq.DSLContext;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@AutoConfiguration
@ConditionalOnClass({EncryptedContentStore.class, DSLContext.class, DSLContextResolver.class})
public class EncryptedContentStoreAutoConfiguration {

    @Bean // TODO: remove this, only used for contentgrid.appserver.content.encryption.bootstrap-tables
    @ConditionalOnMissingBean(DataEncryptionKeyAccessor.class)
    @ConditionalOnBooleanProperty("contentgrid.appserver.content.encryption.enabled")
    TableStorageDataEncryptionKeyAccessor tableStorageEncryptionKeyAccessor(DSLContext dslContext) {
        return new TableStorageDataEncryptionKeyAccessor(dslContext);
    }

    @Primary
    @Bean
    ContentStoreResolver encryptedContentStoreResolver(ContentStoreResolver contentStoreResolver, DSLContextResolver dslContextResolver) {
        return new EncryptedContentStoreResolver(contentStoreResolver, dslContextResolver);
    }

    // TODO: remove this, use a TableCreator which is activated by contentgrid.appserver.query-engine.bootstrap-tables
    @Bean
    @ConditionalOnBean(TableStorageDataEncryptionKeyAccessor.class)
    TableInitializer dekTableInitializer(
            TableStorageDataEncryptionKeyAccessor encryptionKeyAccessor,
            @Value("${contentgrid.appserver.content.encryption.bootstrap-tables:NONE}") Bootstrap bootstrap
    ) {
        return new TableInitializer(encryptionKeyAccessor, bootstrap);
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
