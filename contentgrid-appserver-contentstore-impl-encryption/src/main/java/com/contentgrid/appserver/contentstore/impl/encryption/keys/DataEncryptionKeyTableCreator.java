package com.contentgrid.appserver.contentstore.impl.encryption.keys;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.settings.encryption.ContentEncryptionSettings;
import com.contentgrid.appserver.query.engine.api.TableCreator;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DataEncryptionKeyTableCreator implements TableCreator {

    private final DSLContextResolver dslContextResolver;

    @Override
    public void createTables(Application application) {
        if (application.getApplicationSettings(ContentEncryptionSettings.class).isPresent()) {
            TableStorageDataEncryptionKeyAccessor.setupTables(dslContextResolver.resolve(application));
        }
    }

    @Override
    public void dropTables(Application application) {
        if (application.getApplicationSettings(ContentEncryptionSettings.class).isPresent()) {
            TableStorageDataEncryptionKeyAccessor.dropTables(dslContextResolver.resolve(application));
        }
    }
}
