package com.contentgrid.appserver.autoconfigure.contentstore;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.settings.encryption.ContentEncryptionEngineAlgorithm;
import com.contentgrid.appserver.application.model.settings.encryption.ContentEncryptionKeyWrapperAlgorithm;
import com.contentgrid.appserver.application.model.settings.encryption.ContentEncryptionSettings;
import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.domain.content.ContentStoreResolver;
import com.contentgrid.appserver.contentstore.impl.encryption.EncryptedContentStore;
import com.contentgrid.appserver.contentstore.impl.encryption.engine.AesCtrEncryptionEngine;
import com.contentgrid.appserver.contentstore.impl.encryption.engine.AlfrescoCompatibleEncryptionEngine;
import com.contentgrid.appserver.contentstore.impl.encryption.engine.ContentEncryptionEngine;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.DataEncryptionKeyWrapper;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.TableStorageDataEncryptionKeyAccessor;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.UnencryptedSymmetricDataEncryptionKeyWrapper;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class EncryptedContentStoreResolver implements ContentStoreResolver {

    private final ContentStoreResolver delegate;
    private final DSLContextResolver dslContextResolver;

    @Override
    public ContentStore resolve(Application application) {
        var contentStore = delegate.resolve(application);

        var maybeEncryptionSettings = application.getApplicationSettings(ContentEncryptionSettings.class);
        if (maybeEncryptionSettings.isEmpty()) {
            return contentStore;
        }
        var encryptionSettings = maybeEncryptionSettings.get();
        var encryptionEngines = encryptionSettings.getEncryptionEngineAlgorithms().stream()
                .map(this::contentEncryptionEngineForAlgorithm)
                .toList();
        var encryptionKeyWrappers = encryptionSettings.getKeyWrapperAlgorithms().stream()
                .map(this::dataEncryptionKeyWrapperForAlgorithm)
                .toList();
        var dslContext = dslContextResolver.resolve(application);
        var encryptionKeyAccessor = new TableStorageDataEncryptionKeyAccessor(dslContext);

        return new EncryptedContentStore(contentStore, encryptionKeyAccessor, encryptionKeyWrappers, encryptionEngines);
    }

    private DataEncryptionKeyWrapper dataEncryptionKeyWrapperForAlgorithm(ContentEncryptionKeyWrapperAlgorithm algorithm) {
        return switch (algorithm) {
            case NONE -> new UnencryptedSymmetricDataEncryptionKeyWrapper(true);
        };
    }

    private ContentEncryptionEngine contentEncryptionEngineForAlgorithm(ContentEncryptionEngineAlgorithm algorithm) {
        return switch (algorithm) {
            case AES128_CTR -> new AesCtrEncryptionEngine(128);
            case AES192_CTR -> new AesCtrEncryptionEngine(192);
            case AES256_CTR -> new AesCtrEncryptionEngine(256);
            case ALFRESCO -> new AlfrescoCompatibleEncryptionEngine();
        };
    }
}
