package com.contentgrid.appserver.query.engine.jooq;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint.UniqueConstraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.exceptions.InvalidArgumentModelException;
import com.contentgrid.appserver.application.model.searchfilters.FullTextSearchAttributeSearchFilter;
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
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

import static com.contentgrid.appserver.application.model.searchfilters.BaseAttributeSearchFilter.Operation.FTS;
import static java.util.Locale.ENGLISH;

@Slf4j
@RequiredArgsConstructor
@Transactional
public class JOOQTableCreator implements TableCreator {

    static final @NonNull Set<@NonNull Locale> SUPPORTED_LOCALES = Set.of(
            Locale.of("ar"), // Arabic
            Locale.of("hy"), // Armenian
            Locale.of("eu"), // Basque
            Locale.of("ca"), // Catalan
            Locale.of("da"), // Danish
            Locale.of("nl"), // Dutch
            Locale.of("en"), // English
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
            Locale.of("pt"), // Portuguese
            Locale.of("ro"), // Romanian
            Locale.of("ru"), // Russian
            Locale.of("sr"), // Serbian
            Locale.of("es"), // Spanish
            Locale.of("sv"), // Swedish
            Locale.of("ta"), // Tamil
            Locale.of("tr"), // Turkish
            Locale.of("yi")  // Yiddish
    );

    static final @NonNull String FTS_INDEX_PREPARED_STATEMENT = """
        CREATE INDEX IF NOT EXISTS ?
        ON ?
        USING GIN (to_tsvector(?, coalesce(?, '')));
    """;

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
                .stream()
                .filter(searchFilter -> searchFilter instanceof FullTextSearchAttributeSearchFilter fullTextSearchAttributeSearchFilter && fullTextSearchAttributeSearchFilter.getOperation().equals(FTS))
                .forEach(searchFilter -> createFTSIndex(dslContext, application, entity, (FullTextSearchAttributeSearchFilter) searchFilter));
    }

    void createFTSIndex(@NonNull DSLContext dslContext, @NonNull Application application, @NonNull Entity entity, @NonNull FullTextSearchAttributeSearchFilter searchFilter) {
        Attribute attribute = application.resolvePropertyPath(entity, searchFilter.getAttributePath());
        if (!(attribute instanceof SimpleAttribute simpleAttribute)) throw new InvalidArgumentModelException("Full-text search can only be applied to simple attributes.");
        createFTSIndex(dslContext, entity, simpleAttribute, searchFilter.getLocale());
    }

    @Allow.PlainSQL
    private void createFTSIndex(@NonNull DSLContext dslContext, @NonNull Entity entity,
                                @NonNull SimpleAttribute attribute, @NonNull Locale locale) throws RuntimeException {
        String tableName = entity.getTable().getValue();
        String ftsColumnName = attribute.getColumn().getValue();
        String indexName = "%s_%s_fts_idx".formatted(tableName, ftsColumnName);
        if (!SUPPORTED_LOCALES.contains(locale)) throw new InvalidArgumentModelException("Locale (%s) is not supported for full-text search.".formatted(locale));

        log.debug("Creating an FTS index ({}) on table ({}) for column ({}).", indexName, tableName, ftsColumnName);
        // JOOQ does not seem to be flexible enough to create the FTS index with the required configuration, so we use a prepared statement.
        // Allow.PlainSQL since this code is executed only during the model definition phase, not during request processing.
        dslContext.execute(FTS_INDEX_PREPARED_STATEMENT, DSL.name(indexName), DSL.name(tableName),
                DSL.inline(locale.getDisplayLanguage(ENGLISH)), DSL.inline(ftsColumnName));
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
