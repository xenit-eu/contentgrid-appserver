package com.contentgrid.appserver.application.model.values;

import lombok.Value;

@Value(staticConstructor = "of")
public class AttributeName implements PropertyName {
    String value;

    public static AttributeName from(PropertyName property) {
        return new AttributeName(property.getValue());
    }

    @Override
    public String toString() {
        return getValue();
    }
}
