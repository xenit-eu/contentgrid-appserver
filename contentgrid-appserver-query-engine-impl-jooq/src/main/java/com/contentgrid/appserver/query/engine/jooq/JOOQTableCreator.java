package com.contentgrid.appserver.query.engine.jooq;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint.UniqueConstraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.values.SchemaName;
import com.contentgrid.appserver.query.engine.api.TableCreator;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;
import com.contentgrid.appserver.query.engine.jooq.strategy.JOOQRelationStrategyFactory;
import lombok.RequiredArgsConstructor;
import org.jooq.Allow;
import org.jooq.CreateTableElementListStep;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional
public class JOOQTableCreator implements TableCreator {

    private final DSLContextResolver resolver;

    @Override
    public void createTables(Application application) {
        var dslContext = resolver.resolve(application);
        // Create schema first
        createSchema(dslContext, application);

        for (var entity : application.getEntities()) {
            createTableForEntity(dslContext, entity);
        }
        // Create relations after tables are created, so that each table referenced in the foreign key constraint exists
        for (var relation : application.getRelations()) {
            var strategy = JOOQRelationStrategyFactory.forRelation(relation);
            strategy.make(dslContext, application, relation);
        }
        // Create extensions schema and functions
        createCGPrefixSearchNormalize(dslContext);
    }

    private void createSchema(DSLContext dslContext, Application application) {
        application.getSettings().getDatabase().ifPresent(settings -> {
            if (!SchemaName.PUBLIC.equals(settings.getSchema())) {
                var schema = DSL.schema(DSL.name(settings.getSchema().getValue()));
                dslContext.createSchemaIfNotExists(schema).execute();
            }
        });
    }

    private void dropSchema(DSLContext dslContext, Application application) {
        application.getSettings().getDatabase().ifPresent(settings -> {
            if (!SchemaName.PUBLIC.equals(settings.getSchema())) {
                var schema = DSL.schema(DSL.name(settings.getSchema().getValue()));
                dslContext.dropSchemaIfExists(schema).execute();
            }
        });
    }

    private void createTableForEntity(DSLContext dslContext, Entity entity) {
        var step = dslContext.createTableIfNotExists(entity.getTable().getValue())
                .column(JOOQUtils.resolvePrimaryKey(entity))
                .primaryKey(entity.getPrimaryKey().getColumn().getValue());
        for (var attribute : entity.getAttributes()) {
            step = createColumnsForAttribute(step, attribute);
        }
        step.execute();
    }

    private CreateTableElementListStep createColumnsForAttribute(CreateTableElementListStep step, Attribute attribute) {
        switch (attribute) {
            case SimpleAttribute simpleAttribute -> {
                var result = step.column(JOOQUtils.resolveField(simpleAttribute));
                if (simpleAttribute.hasConstraint(UniqueConstraint.class)) {
                    result = result.constraint(DSL.unique(simpleAttribute.getColumn().getValue()));
                }
                return result;
            }
            case CompositeAttribute compositeAttribute -> {
                for (var nestedAttribute : compositeAttribute.getAttributes()) {
                    step = createColumnsForAttribute(step, nestedAttribute);
                }
                return step;
            }
        }
    }

    @Override
    public void dropTables(Application application) {
        var dslContext = resolver.resolve(application);

        // drop relations first
        for (var relation : application.getRelations()) {
            var strategy = JOOQRelationStrategyFactory.forRelation(relation);
            strategy.destroy(dslContext, application, relation);
        }

        // Drop entity tables after relations are dropped
        for (var entity : application.getEntities()) {
            var table = JOOQUtils.resolveTable(entity);
            dslContext.dropTableIfExists(table).execute();
        }

        // Drop extensions schema and functions
        dropCGPrefixSearchNormalize(dslContext);
        dropSchema(dslContext, application);
    }

    @Allow.PlainSQL
    private void createCGPrefixSearchNormalize(DSLContext dslContext) {
        var schema = DSL.schema("extensions");
        dslContext.createSchemaIfNotExists(schema).execute();
        dslContext.execute(DSL.sql("CREATE EXTENSION IF NOT EXISTS unaccent SCHEMA ?;", schema));
        dslContext.execute(DSL.sql("""
                CREATE OR REPLACE FUNCTION ?.contentgrid_prefix_search_normalize(arg text)
                  RETURNS text
                  LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
                RETURN ?.unaccent('extensions.unaccent', lower(normalize(arg, NFKC)));
                """, schema, schema));
    }

    @Allow.PlainSQL
    private void dropCGPrefixSearchNormalize(DSLContext dslContext) {
        var schema = DSL.schema("extensions");
        dslContext.execute(DSL.sql("DROP FUNCTION IF EXISTS ?.contentgrid_prefix_search_normalize(text);", schema));
        dslContext.execute(DSL.sql("DROP EXTENSION IF EXISTS unaccent;"));
        dslContext.dropSchemaIfExists(schema).execute();
    }

}
