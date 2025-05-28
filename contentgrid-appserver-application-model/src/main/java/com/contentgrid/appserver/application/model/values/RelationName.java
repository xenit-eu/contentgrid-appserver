package com.contentgrid.appserver.application.model.values;

import lombok.Value;

@Value(staticConstructor = "of")
public class RelationName extends PropertyName {
    String value;

}
