package com.contentgrid.appserver.application.model.values;

import lombok.Value;

@Value(staticConstructor = "of")
public class AttributeName extends PropertyName {
    String value;
}
