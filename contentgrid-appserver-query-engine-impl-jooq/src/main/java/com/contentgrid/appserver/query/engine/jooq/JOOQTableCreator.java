package com.contentgrid.appserver.query.engine.jooq;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint.UniqueConstraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.searchfilters.BaseAttributeSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.CompositeAttributeSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.SimpleAttributeSearchFilter;
import com.contentgrid.appserver.application.model.values.PropertyPath;
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
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private static final @NonNull ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    // TODO: allow other languages.
    private static final @NonNull Map<@NonNull String, @NonNull Object> FTS_COLUMN_CONFIG = Map.of("tokenizer", Map.of("type", "default", "stemmer", "English"));

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

    @SneakyThrows
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

        List<SimpleAttribute> ftsAttributes = entity.getSearchFilters()
                .stream().flatMap(searchFilter -> {
                    if (!(searchFilter instanceof BaseAttributeSearchFilter baseAttributeSearchFilter) || !baseAttributeSearchFilter.getOperation().equals(BaseAttributeSearchFilter.Operation.FTS)) return Stream.of();
                    if (baseAttributeSearchFilter instanceof SimpleAttributeSearchFilter simpleAttributeSearchFilter) return Stream.of(simpleAttributeSearchFilter.getAttributePath());
                    if (baseAttributeSearchFilter instanceof CompositeAttributeSearchFilter compositeAttributeSearchFilter) return compositeAttributeSearchFilter.getAttributePaths().stream();
                    throw new IllegalArgumentException("Unknown search filter type (%s).".formatted(searchFilter.getClass().getName()));
                })
                .distinct()
                .map(propertyPath -> application.resolvePropertyPath(entity, propertyPath))
                .toList();
        if (!ftsAttributes.isEmpty())  createFTSIndices(dslContext, entity, ftsAttributes);
    }

    @SneakyThrows
    private void createFTSIndices(@NonNull DSLContext dslContext, @NonNull Entity entity, @NonNull List<@NonNull SimpleAttribute> attributes) throws RuntimeException, JsonProcessingException {
        String tableName = entity.getTable().getValue();
        String idColumnName = entity.getPrimaryKey().getColumn().getValue();
        List<String> ftsColumnNames = attributes.stream().map(attr -> attr.getColumn().getValue()).toList();
        String indexName = "%s_%s_fts_idx".formatted(tableName, String.join("_", ftsColumnNames));

        // TODO: ugly code.
        log.debug("Creating an FTS index ({}) on table ({}) for columns ({}).", indexName, tableName, ftsColumnNames);
        // JOOQ is not flexible enough to create the FTS index with the required configuration, so we use a prepared statement.
        String statement = ftsIndexPreparedStatement.formatted(indexName, tableName, idColumnName, OBJECT_MAPPER.writeValueAsString(ftsColumnNames).replace("[", "").replace("]", ""), idColumnName, OBJECT_MAPPER.writeValueAsString(createFTSIndexTextFieldStatement(ftsColumnNames)));
        System.out.println("Executing FTS index creation statement: " + statement);
        dslContext.execute(statement);
    }

    private @NonNull Map<@NonNull String, @NonNull Object> createFTSIndexTextFieldStatement(@NonNull List<@NonNull String> columnNames) throws JsonProcessingException {
        return columnNames.stream()
                .map(columnName -> Map.entry(columnName, FTS_COLUMN_CONFIG))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
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
            try {
                dslContext.dropTable(table).execute();
            } catch (BadSqlGrammarException e) {
                throw new InvalidSqlException(e.getMessage(), e);
            }
        }
    }

}
