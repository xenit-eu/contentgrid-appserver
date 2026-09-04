package com.contentgrid.appserver.autoconfigure.contentstore;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.settings.encryption.ContentEncryptionSettings;
import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.domain.content.ContentStoreResolver;
import com.contentgrid.appserver.contentstore.impl.encryption.EncryptedContentStore;
import com.contentgrid.appserver.contentstore.impl.encryption.engine.ContentEncryptionEngine;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.DataEncryptionKeyWrapper;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.TableStorageDataEncryptionKeyAccessor;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class EncryptedContentStoreResolver implements ContentStoreResolver {

    private final ContentStoreResolver delegate;
    private final DSLContextResolver dslContextResolver;
    private final List<DataEncryptionKeyWrapper> encryptionKeyWrappers;
    private final List<ContentEncryptionEngine> encryptionEngines;

    @Override
    public ContentStore resolve(Application application) {
        var contentStore = delegate.resolve(application);

        var encryptionEnabled = application.getSettings().getContentEncryption()
                .filter(ContentEncryptionSettings::isEnabled)
                .isPresent();
        if (!encryptionEnabled) {
            return contentStore;
        }
        var dslContext = dslContextResolver.resolve(application);
        var encryptionKeyAccessor = new TableStorageDataEncryptionKeyAccessor(dslContext);

        return new EncryptedContentStore(contentStore, encryptionKeyAccessor, encryptionKeyWrappers, encryptionEngines);
    }
}
