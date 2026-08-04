package com.contentgrid.appserver.autoconfigure.database;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.settings.database.DatabaseSettings;
import com.contentgrid.appserver.application.model.values.SchemaName;
import java.util.Optional;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ApplicationDatabaseSchema {

    /**
     * The database schema that the SQL statements for an application have to be executed in, as configured by
     * {@code settings.database.schema} in the application model.
     * <p>
     * Empty when the application uses the default schema of the connection. {@link SchemaName#PUBLIC} is the
     * default schema, so it is reported as such: it is never created or dropped, and the {@code search_path} of
     * the connection is left alone.
     */
    public static Optional<SchemaName> of(Application application) {
        return application.getSettings().getDatabase()
                .map(DatabaseSettings::getSchema)
                .filter(schema -> !SchemaName.PUBLIC.equals(schema));
    }
}
