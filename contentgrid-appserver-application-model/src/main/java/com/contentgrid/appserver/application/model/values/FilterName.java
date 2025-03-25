package com.contentgrid.appserver.application.model.values;

import lombok.NonNull;
import lombok.Value;

@Value(staticConstructor = "of")
public class FilterName {

    @NonNull
    String value;

    @Override
    public String toString() {
        return getValue();
    }

    public FilterName withSuffix(String suffix) {
        return FilterName.of(value + suffix);
    }
}
