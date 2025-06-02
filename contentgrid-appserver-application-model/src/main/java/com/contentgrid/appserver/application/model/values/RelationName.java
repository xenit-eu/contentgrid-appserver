package com.contentgrid.appserver.application.model.values;

import lombok.EqualsAndHashCode;
import lombok.NonNull;

@EqualsAndHashCode(callSuper = true)
public class RelationName extends PropertyName {

    private RelationName(@NonNull String value) {
        super(value);
    }

    public static RelationName of(@NonNull String value) {
        return new RelationName(value);
    }

    public static RelationName from(@NonNull PropertyName property) {
        return new RelationName(property.getValue());
    }
}
