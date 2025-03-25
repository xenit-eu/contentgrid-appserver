package com.contentgrid.appserver.application.model.values;

import lombok.NonNull;
import lombok.Value;

@Value(staticConstructor = "of")
public class ColumnName {

    @NonNull
    String value;

    @Override
    public String toString() {
        return getValue();
    }

    public ColumnName withSuffix(String suffix) {
        return ColumnName.of(value + suffix);
    }
}
