package com.contentgrid.appserver.application.model.values;

public sealed interface PropertyName permits AttributeName, RelationName {
    String getValue();
}
