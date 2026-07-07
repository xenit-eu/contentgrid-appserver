package com.contentgrid.appserver.query.engine.jooq.resolver;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.SchemaName;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.conf.MappedSchema;
import org.jooq.conf.RenderMapping;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;

@RequiredArgsConstructor
public class AutowiredDSLContextResolver implements DSLContextResolver {

    private final DSLContext dslContext;

    @Override
    public DSLContext resolve(Application application) {
        var maybeDatabaseSettings = application.getSettings().getDatabase();
        if (maybeDatabaseSettings.isEmpty()) {
            return dslContext;
        }
        var databaseSettings = maybeDatabaseSettings.get();

        var derivedConfig = dslContext.configuration()
                .deriveSettings(settings -> configureSchema(settings, databaseSettings.getSchema()));

        return DSL.using(derivedConfig);
    }

    private Settings configureSchema(Settings settings, SchemaName schemaName) {
        if (schemaName == null) {
            return settings;
        }
        var renderMapping = settings.getRenderMapping() == null ? new RenderMapping() : settings.getRenderMapping();
        var schemata = new ArrayList<>(renderMapping.getSchemata());

        // Replace default/empty schema with the configured schema in generated sql
        schemata.add(new MappedSchema().withInput("").withOutput(schemaName.getValue()));

        return settings.withRenderMapping(renderMapping.withSchemata(schemata));
    }
}
