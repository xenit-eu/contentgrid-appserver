package com.contentgrid.appserver.application.model.values;

import lombok.NonNull;
import lombok.Value;

@Value(staticConstructor = "of")
public class AttributeName implements PropertyName {

    @NonNull
    String value;

    @Override
    public String toString() {
        return getValue();
    }
}
