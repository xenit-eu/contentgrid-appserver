package com.contentgrid.appserver.application.model.values;

import java.io.Serializable;

public sealed interface PropertyName extends Serializable permits AttributeName, RelationName {
    String getValue();
}
