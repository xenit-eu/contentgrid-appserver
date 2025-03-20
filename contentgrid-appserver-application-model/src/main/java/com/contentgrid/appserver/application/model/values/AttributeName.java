package com.contentgrid.appserver.application.model.values;

import lombok.NonNull;
import lombok.Value;

@Value(staticConstructor = "of")
public class AttributeName {

    @NonNull
    String value;
}
