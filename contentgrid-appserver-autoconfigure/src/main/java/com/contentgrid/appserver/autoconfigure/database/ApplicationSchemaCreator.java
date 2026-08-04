package com.contentgrid.appserver.autoconfigure.database;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.query.engine.api.TableCreator;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;
import lombok.RequiredArgsConstructor;
import org.jooq.impl.DSL;

/**
 * Creates the database schema configured in the application model, so the tables of the application can be
 * created in it.
 * <p>
 * A deployed application gets its schema from the database migrations of its blueprint artifact; this only
 * covers bootstrapping the tables with {@code contentgrid.appserver.query-engine.bootstrap-tables}. It has to
 * run before the other {@link TableCreator}s, since those create their tables in this schema.
 */
@RequiredArgsConstructor
public class ApplicationSchemaCreator implements TableCreator {

    private final DSLContextResolver dslContextResolver;

    @Override
    public void createTables(Application application) {
        ApplicationDatabaseSchema.of(application).ifPresent(schema -> dslContextResolver.resolve(application)
                .createSchemaIfNotExists(DSL.name(schema.getValue()))
                .execute());
    }

    @Override
    public void dropTables(Application application) {
        ApplicationDatabaseSchema.of(application).ifPresent(schema -> dslContextResolver.resolve(application)
                .dropSchemaIfExists(DSL.name(schema.getValue()))
                .execute());
    }
}
