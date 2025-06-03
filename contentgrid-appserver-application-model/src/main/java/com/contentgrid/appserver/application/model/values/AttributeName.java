package com.contentgrid.appserver.application.model.values;

import lombok.Value;

@Value(staticConstructor = "of")
public class AttributeName implements PropertyName {
    String value;

    @Override
    public String toString() {
        return getValue();
    }
}
