package com.contentgrid.appserver.application.model.values;

import lombok.NonNull;
import lombok.Value;

@Value(staticConstructor = "of")
public class EntityName {

    @NonNull
    String value;

    @Override
    public String toString() {
        return getValue();
    }

    public TableName toTableName() {
        return TableName.of(value.replace('-', '_'));
    }
}
