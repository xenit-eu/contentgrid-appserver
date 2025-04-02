package com.contentgrid.appserver.query.engine;

import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.query.engine.model.CGColumnFilter;
import com.contentgrid.appserver.query.engine.model.CGColumnFilter.Operator;
import com.contentgrid.appserver.query.engine.model.CGDelete;
import com.contentgrid.appserver.query.engine.model.CGInsert;
import com.contentgrid.appserver.query.engine.model.CGQuery;
import com.contentgrid.appserver.query.engine.model.CGUpdate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface QueryEngine {

    List<Map<ColumnName, Object>> findAll(CGQuery query);

    default Optional<Map<ColumnName, Object>> findById(TableName table, ColumnName idColumn, UUID value) {
        var query = CGQuery.builder()
                .table(table)
                .alias(table)
                .columnFilter(CGColumnFilter.builder()
                        .table(table)
                        .column(idColumn)
                        .operator(Operator.EQUALS)
                        .value(value)
                        .build())
                .build();
        return findAll(query)
                .stream().findAny();
    }

    UUID create(CGInsert insert);

    void update(CGUpdate update);

    void delete(CGDelete delete);

    void deleteAll(TableName table);

}
