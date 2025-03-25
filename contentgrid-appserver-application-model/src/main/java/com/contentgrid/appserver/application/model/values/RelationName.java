package com.contentgrid.appserver.application.model.values;

import lombok.NonNull;
import lombok.Value;

@Value(staticConstructor = "of")
public class RelationName {

    @NonNull
    String value;

    @Override
    public String toString() {
        return getValue();
    }

    public ColumnName toColumnName() {
        return ColumnName.of(value);
    }
}
