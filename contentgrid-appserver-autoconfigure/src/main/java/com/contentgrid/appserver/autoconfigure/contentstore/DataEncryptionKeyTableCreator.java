package com.contentgrid.appserver.autoconfigure.contentstore;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.settings.encryption.ContentEncryptionSettings;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.TableStorageDataEncryptionKeyAccessor;
import com.contentgrid.appserver.query.engine.api.TableCreator;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DataEncryptionKeyTableCreator implements TableCreator {

    private final DSLContextResolver dslContextResolver;

    @Override
    public void createTables(Application application) {
        if (isContentEncryptionEnabled(application)) {
            TableStorageDataEncryptionKeyAccessor.setupTables(dslContextResolver.resolve(application));
        }
    }

    @Override
    public void dropTables(Application application) {
        if (isContentEncryptionEnabled(application)) {
            TableStorageDataEncryptionKeyAccessor.dropTables(dslContextResolver.resolve(application));
        }
    }

    private static boolean isContentEncryptionEnabled(Application application) {
        return application.getSettings().getContentEncryption()
                .filter(ContentEncryptionSettings::isEnabled)
                .isPresent();
    }
}
