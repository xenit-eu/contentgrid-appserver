package com.contentgrid.appserver.application.model.values;

import lombok.Value;

@Value(staticConstructor = "of")
public class RelationName implements PropertyName {
    String value;

    public static RelationName from(PropertyName property) {
        return new RelationName(property.getValue());
    }
}
