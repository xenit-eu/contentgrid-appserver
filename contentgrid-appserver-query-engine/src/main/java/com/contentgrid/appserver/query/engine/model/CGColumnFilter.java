package com.contentgrid.appserver.query.engine.model;

import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.TableName;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class CGColumnFilter implements CGFilter {

    @NonNull
    TableName table;

    @NonNull
    ColumnName column;

    @NonNull
    Operator operator;

    @NonNull
    Object value;

    public enum Operator {
        EQUALS,
        GREATER_THAN,
        GREATER_THAN_OR_EQUALS,
        LESS_THAN,
        LESS_THAN_OR_EQUALS,
        EQUALS_NORMALIZED,
        CG_PREFIX

    }
}
