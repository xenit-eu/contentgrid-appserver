package com.contentgrid.appserver.query.engine.model;

import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.TableName;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class CGUpdate {
    @NonNull
    TableName table;

    @Singular
    Map<ColumnName, Object> pairs;

    @Singular
    List<CGColumnFilter> conditions;

}
