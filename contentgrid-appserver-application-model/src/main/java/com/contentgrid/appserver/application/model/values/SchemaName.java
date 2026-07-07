package com.contentgrid.appserver.application.model.values;

import lombok.NonNull;
import lombok.Value;

@Value(staticConstructor = "of")
public class SchemaName {

    @NonNull
    String value;

    @Override
    public String toString() {
        return getValue();
    }

    public static final SchemaName PUBLIC = SchemaName.of("public");
}
