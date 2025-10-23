package com.contentgrid.appserver.query.engine.jooq;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint.UniqueConstraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.exceptions.InvalidArgumentModelException;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter;
import com.contentgrid.appserver.query.engine.api.TableCreator;
import com.contentgrid.appserver.query.engine.api.exception.InvalidSqlException;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;
import com.contentgrid.appserver.query.engine.jooq.strategy.JOOQRelationStrategyFactory;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Allow;
import org.jooq.CreateTableElementListStep;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static java.util.Locale.ENGLISH;
import static java.util.Map.entry;

@Slf4j
@RequiredArgsConstructor
@Transactional
public class JOOQTableCreator implements TableCreator {

    private static final @NonNull Set<@NonNull Locale> SUPPORTED_LOCALES = Set.of(
            Locale.of("ar"), // Arabic
            Locale.of("hy"), // Armenian
            Locale.of("eu"), // Basque
            Locale.of("ca"), // Catalan
            Locale.of("da"), // Danish
            Locale.of("nl"), // Dutch
            Locale.of("en"), // English
            Locale.of("et"), // Estonian
            Locale.of("fi"), // Finnish
            Locale.of("fr"), // French
            Locale.of("de"), // German
            Locale.of("el"), // Greek
            Locale.of("hi"), // Hindi
            Locale.of("hu"), // Hungarian
            Locale.of("id"), // Indonesian
            Locale.of("ga"), // Irish
            Locale.of("it"), // Italian
            Locale.of("lt"), // Lithuanian
            Locale.of("ne"), // Nepali
            Locale.of("no"), // Norwegian
            Locale.of("pt-PT"), // Portuguese (Portugal)
            Locale.of("ro"), // Romanian
            Locale.of("ru"), // Russian
            Locale.of("es"), // Spanish
            Locale.of("sv"), // Swedish
            Locale.of("ta"), // Tamil
            Locale.of("tr"), // Turkish
            Locale.of("yi")  // Yiddish
    );
    private static final @NonNull String ftsIndexPreparedStatement;

    static {
        try (InputStream inputStream = new ClassPathResource("com/contentgrid/appserver/query/engine/jooq/sql/statements/create_fts_index.sql").getInputStream()) {
            ftsIndexPreparedStatement = new String(inputStream.readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private final DSLContextResolver resolver;

    @Override
    public void createTables(@NonNull Application application) {
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

    private void createTableForEntity(@NonNull DSLContext dslContext, @NonNull Application application, @NonNull Entity entity) {
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

        // Create FTS indices.
        entity.getSearchFilters()
                .stream().filter(searchFilter -> searchFilter instanceof AttributeSearchFilter && ((AttributeSearchFilter) searchFilter).getOperation().equals(AttributeSearchFilter.Operation.FTS))
                .map(searchFilter -> application.resolvePropertyPath(entity, ((AttributeSearchFilter) searchFilter).getAttributePath()))
                .distinct()
                .forEach(simpleAttribute -> createFTSIndex(dslContext, entity, simpleAttribute));
    }

    @Allow.PlainSQL
    private void createFTSIndex(@NonNull DSLContext dslContext, @NonNull Entity entity, @NonNull SimpleAttribute attribute) throws RuntimeException {
        String tableName = entity.getTable().getValue();
        String ftsColumnName = attribute.getColumn().getValue();
        String indexName = "%s_%s_fts_idx".formatted(tableName, ftsColumnName);
        Locale attributeLocale = attribute.getLocale();
        if (!SUPPORTED_LOCALES.contains(attributeLocale)) throw new InvalidArgumentModelException("Locale (%s) is not supported for full-text search.".formatted(attributeLocale));

        log.debug("Creating an FTS index ({}) on table ({}) for column ({}).", indexName, tableName, ftsColumnName);
        // JOOQ is not flexible enough to create the FTS index with the required configuration, so we use a prepared statement.
        // Prepared statement template expects: indexName, tableName, tsConfig, columnName
        dslContext.execute(ftsIndexPreparedStatement.formatted(indexName, tableName, attributeLocale.getDisplayLanguage(ENGLISH), ftsColumnName));
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
