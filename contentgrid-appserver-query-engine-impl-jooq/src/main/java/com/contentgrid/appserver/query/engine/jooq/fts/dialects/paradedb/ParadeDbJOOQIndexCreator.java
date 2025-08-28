package com.contentgrid.appserver.query.engine.jooq.fts.dialects.paradedb;

import static org.jooq.impl.DSL.val;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.searchfilters.FullTextSearchFilter;
import com.contentgrid.appserver.query.engine.api.IndexCreator;
import com.contentgrid.appserver.query.engine.jooq.JOOQUtils;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Index;
import org.jooq.Table;
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
        var dslContext = contextResolver.resolve(application);
        for (var entity : application.getEntities()) {
            dropIndexForEntity(dslContext, entity);
        }
    }

    private void createIndexForEntity(DSLContext dslContext, Entity entity) {
        // select columns
        List<String> indexedColumns = entity.getSearchFilters().stream()
                .filter(searchFilter -> searchFilter instanceof FullTextSearchFilter)
                .map(FullTextSearchFilter.class::cast)
                .map(fullTextSearchFilter -> fullTextSearchFilter.getAttributePath().toString())
                .collect(Collectors.toList());
        if (indexedColumns.isEmpty()) {
            return;
        }
        Index idxName = JOOQUtils.resolveSingleEntityFtsIndex(entity);
        Table<?> targetTable = JOOQUtils.resolveTable(entity);
        var keyField = entity.getPrimaryKey().getColumn().getValue();
        indexedColumns.addFirst(keyField);
        dslContext.query(
                "CREATE INDEX :idxName ON :targetTable USING bm25(:indexedColumnNames) WITH (key_field=:keyField)",
                idxName.getName(),
                targetTable,
                JOOQUtils.commaSeparatedListOfFields(indexedColumns),
                keyField
        ).execute();
    }

    private void dropIndexForEntity(DSLContext dslContext, Entity entity) {
        Index idxName = JOOQUtils.resolveSingleEntityFtsIndex(entity);
        dslContext.dropIndex(idxName).execute();
    }
}
