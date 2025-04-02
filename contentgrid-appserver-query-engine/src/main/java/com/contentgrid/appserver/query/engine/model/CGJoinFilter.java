package com.contentgrid.appserver.query.engine.model;

import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.TableName;
import java.util.List;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class CGJoinFilter implements CGFilter {

    @NonNull
    CGJoin join;

    @Singular
    List<CGColumnFilter> filters;

    @Value
    @Builder
    public static class CGJoin {

        @NonNull TableName leftTable;
        @NonNull ColumnName leftColumn;
        @NonNull TableName rightTable;
        @NonNull ColumnName rightColumn;
    }
}
