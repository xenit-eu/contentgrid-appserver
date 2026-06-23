package com.contentgrid.appserver.contentstore.impl.encryption.resolver;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.settings.encryption.ContentEncryptionSettings;
import com.contentgrid.appserver.application.model.settings.encryption.ContentEncryptionEngineAlgorithm;
import com.contentgrid.appserver.application.model.settings.encryption.ContentEncryptionKeyWrapperAlgorithm;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.contentstore.api.resolver.ContentStoreResolver;
import com.contentgrid.appserver.contentstore.impl.encryption.EncryptedContentStore;
import com.contentgrid.appserver.contentstore.impl.utils.testing.MockContentStore;
import com.contentgrid.appserver.query.engine.jooq.resolver.AutowiredDSLContextResolver;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;
import org.jooq.CloseableDSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

class EncryptedContentStoreResolverTest {

    @AutoClose
    private final CloseableDSLContext dslContext = DSL.using("jdbc:h2:mem:test", "sa", "sa");
    private final DSLContextResolver dslContextResolver = new AutowiredDSLContextResolver(dslContext);

    @AutoClose
    private final ContentStore contentStore = new MockContentStore();
    private final ContentStoreResolver backingContentStoreResolver = application -> contentStore;

    private final ContentStoreResolver encryptedContentStoreResolver = new EncryptedContentStoreResolver(backingContentStoreResolver, dslContextResolver);

    @Test
    void resolveWithoutEncryption() {
        var application = Application.builder()
                .name(ApplicationName.of("default"))
                .build();

        assertThat(encryptedContentStoreResolver.resolve(application)).isInstanceOf(MockContentStore.class);
    }

    @Test
    void resolveWithEncryption() {
        var application = Application.builder()
                .name(ApplicationName.of("default"))
                .applicationSetting(ContentEncryptionSettings.builder()
                        .encryptionEngineAlgorithm(ContentEncryptionEngineAlgorithm.AES128_CTR)
                        .keyWrapperAlgorithm(ContentEncryptionKeyWrapperAlgorithm.NONE)
                        .build())
                .build();

        assertThat(encryptedContentStoreResolver.resolve(application)).isInstanceOf(EncryptedContentStore.class);
    }

    @Test
    void resolveWithMultipleEncryptionEngineAlgorithms() {
        var application = Application.builder()
                .name(ApplicationName.of("default"))
                .applicationSetting(ContentEncryptionSettings.builder()
                        .encryptionEngineAlgorithm(ContentEncryptionEngineAlgorithm.AES128_CTR)
                        .encryptionEngineAlgorithm(ContentEncryptionEngineAlgorithm.AES192_CTR)
                        .encryptionEngineAlgorithm(ContentEncryptionEngineAlgorithm.AES256_CTR)
                        .encryptionEngineAlgorithm(ContentEncryptionEngineAlgorithm.ALFRESCO)
                        .keyWrapperAlgorithm(ContentEncryptionKeyWrapperAlgorithm.NONE)
                        .build())
                .build();

        assertThat(encryptedContentStoreResolver.resolve(application)).isInstanceOf(EncryptedContentStore.class);
    }

}