package com.contentgrid.appserver.application.model.values;

import lombok.EqualsAndHashCode;
import lombok.NonNull;

@EqualsAndHashCode(callSuper = true)
public class AttributeName extends PropertyName {

    private AttributeName(@NonNull String value) {
        super(value);
    }

    public static AttributeName of(@NonNull String value) {
        return new AttributeName(value);
    }

    public static AttributeName from(@NonNull PropertyName property) {
        return new AttributeName(property.getValue());
    }
}
