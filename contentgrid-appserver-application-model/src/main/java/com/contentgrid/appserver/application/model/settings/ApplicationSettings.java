package com.contentgrid.appserver.application.model.settings;

import com.contentgrid.appserver.application.model.settings.database.DatabaseSettings;
import com.contentgrid.appserver.application.model.settings.encryption.ContentEncryptionSettings;
import java.util.Optional;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ApplicationSettings {

    ContentEncryptionSettings contentEncryption;
    DatabaseSettings database;

    public Optional<ContentEncryptionSettings> getContentEncryption() {
        return Optional.ofNullable(contentEncryption);
    }

    public Optional<DatabaseSettings> getDatabase() {
        return Optional.ofNullable(database);
    }

}
