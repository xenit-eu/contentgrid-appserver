package com.contentgrid.appserver.query.engine.jooq.fts.dialects.paradedb;

import static org.jooq.impl.DSL.val;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.searchfilters.FullTextSearchFilter;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.query.engine.api.IndexCreator;
import com.contentgrid.appserver.query.engine.jooq.JOOQUtils;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional
public class ParadeDbJOOQIndexCreator implements IndexCreator {

    private final DSLContextResolver contextResolver;

    @Override
    public void createIndex(Application application) {
        var dslContext = contextResolver.resolve(application);
        for (var entity : application.getEntities()) {
            createIndexForEntity(dslContext, entity);
        }

    }

    @Override
    public void dropIndex(Application application) {

    }

    private void createIndexForEntity(DSLContext dslContext, Entity entity) {
        // select columns
        Set<FullTextSearchFilter> indexedColumns = entity.getSearchFilters().stream()
                .filter(searchFilter -> searchFilter instanceof FullTextSearchFilter)
                .map(FullTextSearchFilter.class::cast)
                .collect(Collectors.toUnmodifiableSet());
        Field<String> idxName = val("");
        Field<String> targetTable = val("");
        String indexedColumnNames = indexedColumns.stream()
                .map(fullTextSearchFilter -> fullTextSearchFilter.getAttributePath().toString())
                .collect(Collectors.joining(","));
        var keyField = entity.getPrimaryKey().getColumn().getValue();

        dslContext.query(
                "CREATE INDEX :idxName ON :targetTable USING bm25(:indexedColumnNames) WITH (key_field=:keyField)",
                idxName,
                targetTable,
                indexedColumnNames,
                keyField
        );
    }
}
