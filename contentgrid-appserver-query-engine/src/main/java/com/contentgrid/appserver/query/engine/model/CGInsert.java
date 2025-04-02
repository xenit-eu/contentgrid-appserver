package com.contentgrid.appserver.query.engine.model;

import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.TableName;
import java.util.Map;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class CGInsert {

    @NonNull
    TableName table;

    @NonNull
    ColumnName primaryKey;

    @Singular
    Map<ColumnName, Object> pairs;

}
