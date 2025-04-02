package com.contentgrid.appserver.query.engine.model;

import com.contentgrid.appserver.application.model.values.TableName;
import java.util.List;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class CGQuery {

    @NonNull
    TableName table;

    @NonNull
    TableName alias;

    @Singular
    List<CGColumnFilter> columnFilters;

    @Singular
    List<CGJoinFilter> joinFilters;
}
