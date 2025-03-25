package com.contentgrid.appserver.application.model.values;

import lombok.NonNull;
import lombok.Value;

@Value(staticConstructor = "of")
public class AttributeName {

    @NonNull
    String value;

    @Override
    public String toString() {
        return getValue();
    }

    public ColumnName toColumnName() {
        return ColumnName.of(value);
    }

    public FilterName toFilterName() {
        return FilterName.of(value);
    }
}
