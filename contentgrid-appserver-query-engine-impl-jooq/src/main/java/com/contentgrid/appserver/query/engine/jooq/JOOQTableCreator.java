package com.contentgrid.appserver.query.engine.jooq;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint.UniqueConstraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter;
import com.contentgrid.appserver.query.engine.api.TableCreator;
import com.contentgrid.appserver.query.engine.api.exception.InvalidSqlException;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;
import com.contentgrid.appserver.query.engine.jooq.strategy.JOOQRelationStrategyFactory;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
@RequiredArgsConstructor
@Transactional
public class JOOQTableCreator implements TableCreator {

    private static final @NonNull String ftsIndexPreparedStatement;

    static {
        try (InputStream inputStream = new ClassPathResource("com/contentgrid/appserver/query/engine/jooq/statements/create_fts_index.sql").getInputStream()) {
            ftsIndexPreparedStatement = new String(inputStream.readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private final DSLContextResolver resolver;

    @Override
    public void createTables(Application application) {
        var dslContext = resolver.resolve(application);
        for (var entity : application.getEntities()) {
            createTableForEntity(dslContext, application, entity);
        }
        // Create relations after tables are created, so that each table referenced in the foreign key constraint exists
        for (var relation : application.getRelations()) {
            var strategy = JOOQRelationStrategyFactory.forRelation(relation);
            strategy.make(dslContext, application, relation);
        }
    }

    private void createTableForEntity(DSLContext dslContext, Application application, Entity entity) {
        var step = dslContext.createTable(entity.getTable().getValue())
                .column(JOOQUtils.resolvePrimaryKey(entity))
                .primaryKey(entity.getPrimaryKey().getColumn().getValue());
        for (var attribute : entity.getAttributes()) {
            step = createColumnsForAttribute(step, attribute);
        }
        try {
            step.execute();
        } catch (BadSqlGrammarException e) {
            throw new InvalidSqlException(e.getMessage(), e);
        }

        // Create FTS indexes.
        entity.getSearchFilters()
                .stream()
                .filter(searchFilter -> searchFilter instanceof AttributeSearchFilter attributeSearchFilter &&
                        attributeSearchFilter.getOperation().equals(AttributeSearchFilter.Operation.FTS))
                .map(searchFilter -> application.resolvePropertyPath(entity, ((AttributeSearchFilter) searchFilter).getAttributePath()))
                .forEach(simpleAttribute -> createFTSIndex(dslContext, entity, simpleAttribute));
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

    private void createFTSIndex(@NonNull DSLContext dslContext, @NonNull Entity entity, @NonNull SimpleAttribute attribute) throws RuntimeException {
        String tableName = entity.getTable().getValue();
        String idColumnName = entity.getPrimaryKey().getColumn().getValue();
        String ftsColumnName = attribute.getColumn().getValue();
        String indexName = "%s_%s_fts_idx".formatted(tableName, ftsColumnName);

        log.debug("Creating an FTS index ({}) on table ({}) for column ({}).", indexName, tableName, ftsColumnName);
        // JOOQ is not flexible enough to create the FTS index with the required configuration, so we use a prepared statement.
        dslContext.execute(ftsIndexPreparedStatement.formatted(indexName, tableName, idColumnName, ftsColumnName, idColumnName, ftsColumnName));
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
            try {
                dslContext.dropTable(table).execute();
            } catch (BadSqlGrammarException e) {
                throw new InvalidSqlException(e.getMessage(), e);
            }
        }
    }

}
