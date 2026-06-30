package com.contentgrid.appserver.autoconfigure.contentstore;

import com.contentgrid.appserver.domain.content.ContentStoreResolver;
import com.contentgrid.appserver.contentstore.impl.encryption.EncryptedContentStore;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.DataEncryptionKeyTableCreator;

import com.contentgrid.appserver.query.engine.api.TableCreator;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;

import org.jooq.DSLContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@AutoConfiguration
@ConditionalOnClass({EncryptedContentStore.class, DSLContext.class, DSLContextResolver.class})
public class EncryptedContentStoreAutoConfiguration {

    @Primary
    @Bean
    ContentStoreResolver encryptedContentStoreResolver(ContentStoreResolver contentStoreResolver, DSLContextResolver dslContextResolver) {
        return new EncryptedContentStoreResolver(contentStoreResolver, dslContextResolver);
    }

    @Bean
    TableCreator dataEncryptionKeyTableCreator(DSLContextResolver dslContextResolver) {
        return new DataEncryptionKeyTableCreator(dslContextResolver);
    }
}
