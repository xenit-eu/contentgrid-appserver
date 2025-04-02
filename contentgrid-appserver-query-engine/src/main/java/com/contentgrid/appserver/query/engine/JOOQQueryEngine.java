package com.contentgrid.appserver.query.engine;

import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.query.engine.model.CGColumnFilter;
import com.contentgrid.appserver.query.engine.model.CGDelete;
import com.contentgrid.appserver.query.engine.model.CGInsert;
import com.contentgrid.appserver.query.engine.model.CGJoinFilter;
import com.contentgrid.appserver.query.engine.model.CGQuery;
import com.contentgrid.appserver.query.engine.model.CGUpdate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SelectOnConditionStep;
import org.jooq.UpdateSetMoreStep;
import org.jooq.impl.DSL;

@RequiredArgsConstructor
public class JOOQQueryEngine implements QueryEngine {

    @NonNull
    private final DSLContext dslContext;

    @Override
    public List<Map<ColumnName, Object>> findAll(CGQuery query) {
        var select = dslContext.select(DSL.asterisk())
                .from(DSL.table(query.getTable().getValue()).as(DSL.name(query.getAlias().getValue())));
        SelectOnConditionStep<?> step = null;

        // Joins first
        for (var joinFilter : query.getJoinFilters()) {
            if (step == null) {
                step = select.join(DSL.table(joinFilter.getJoin().getLeftTable().getValue()).as(DSL.name("left")))
                        .on(handleJoinFilter(joinFilter));
            } else {
                step = step.join(DSL.table(joinFilter.getJoin().getLeftTable().getValue()).as(DSL.name("left")))
                        .on(handleJoinFilter(joinFilter));
            }
        }

        // Conditions second
        Condition condition = handleColumnFilters(query.getColumnFilters(), query.getAlias());

        // Fetch query
        List<Map<String, Object>> result;
        if (condition != null) {
            if (step != null) {
                result = step.where(condition).fetch().intoMaps();
            } else {
                result = select.where(condition).fetch().intoMaps();
            }
        } else if (step != null) {
            result = step.fetch().intoMaps();
        } else {
            result = select.fetch().intoMaps();
        }

        // Transform Strings to ColumnNames
        return result.stream().map(dict -> {
            Map<ColumnName, Object> hashMap = new HashMap<>();
            for (var entry : dict.entrySet()) {
                hashMap.put(ColumnName.of(entry.getKey()), entry.getValue());
            }
            // TODO: copyOf throws NullPointerException if one of the values is null
            return hashMap; // Map.copyOf(hashMap);
        }).toList();
    }

    private Condition handleColumnFilters(Collection<CGColumnFilter> filters, TableName alias) {
        Condition condition = null;
        for (var filter : filters) {
            if (condition == null) {
                condition = handleColumnFilter(filter, alias);
            } else {
                condition = condition.and(handleColumnFilter(filter, alias));
            }
        }

        return condition;
    }

    private Condition handleColumnFilter(CGColumnFilter filter, TableName alias) {
        var field = toField(alias, filter.getColumn());
        return switch (filter.getOperator()) {
            case EQUALS ->  field.eq(filter.getValue());
            case LESS_THAN -> field.lessThan(filter.getValue());
            case LESS_THAN_OR_EQUALS -> field.lessOrEqual(filter.getValue());
            case GREATER_THAN -> field.greaterThan(filter.getValue());
            case GREATER_THAN_OR_EQUALS -> field.greaterOrEqual(filter.getValue());
            case EQUALS_NORMALIZED -> DSL.field("normalize(" + alias + "." + filter.getColumn() + ", NFKC)")
                    .eq(DSL.field("normalize({0}, NFKC)", String.class, filter.getValue()));
            case CG_PREFIX -> DSL.field("extensions.contentgrid_prefix_search_normalize(" + alias + "." + filter.getColumn() + ")", String.class)
                    .like(DSL.field("extensions.contentgrid_prefix_search_normalize({0}) || '%'", String.class, filter.getValue()));
        };
    }

    private Condition handleJoinFilter(CGJoinFilter filter) {
        var condition = toField(filter.getJoin().getLeftTable(), filter.getJoin().getLeftColumn())
                .eq(toField(filter.getJoin().getRightTable(), filter.getJoin().getRightColumn()));
        for (var columnFilter : filter.getFilters()) {
            condition = condition.and(handleColumnFilter(columnFilter, columnFilter.getTable()));
        }
        return condition;
    }

    @Override
    public UUID create(CGInsert insert) {
        var newId = UUID.randomUUID();
        var step = dslContext.insertInto(DSL.table(insert.getTable().getValue()))
                .set(toField(insert.getTable(), insert.getPrimaryKey()), newId);
        for (var pair : insert.getPairs().entrySet()) {
            step = step.set(toField(insert.getTable(), pair.getKey()), pair.getValue());
        }
        step.execute();
        return newId;
    }

    @Override
    public void update(CGUpdate update) {
        var firstStep = dslContext.update(DSL.table(update.getTable().getValue()));
        UpdateSetMoreStep<?> step = null;
        for (var pair : update.getPairs().entrySet()) {
            if (step == null) {
                step = firstStep.set(toField(update.getTable(), pair.getKey()), pair.getValue());
            } else {
                step = step.set(toField(update.getTable(), pair.getKey()), pair.getValue());
            }
        }
        var condition = handleColumnFilters(update.getConditions(), update.getTable());
        if (step != null) {
            if (condition != null) {
                step.where(condition).execute();
            } else {
                step.execute();
            }
        } else {
            throw new UnsupportedOperationException("No values to update");
        }
    }

    @Override
    public void delete(CGDelete delete) {
        var step = dslContext.deleteFrom(DSL.table(delete.getTable().getValue()));
        var condition = handleColumnFilters(delete.getConditions(), delete.getTable());
        if (condition != null) {
            step.where(condition).execute();
        } else {
            // User might have made some mistake, prevent deleting all data
            throw new UnsupportedOperationException("Deleting all rows not supported, use deleteAll() instead");
        }
    }

    @Override
    public void deleteAll(TableName table) {
        dslContext.deleteFrom(DSL.table(table.getValue())).execute();
    }

    private static Field<Object> toField(TableName table, ColumnName column) {
        return DSL.field(DSL.name(table.getValue(), column.getValue()));
    }
}
