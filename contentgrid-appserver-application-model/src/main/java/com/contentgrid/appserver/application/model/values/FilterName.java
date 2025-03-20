package com.contentgrid.appserver.application.model.values;

import lombok.NonNull;
import lombok.Value;

@Value(staticConstructor = "of")
public class FilterName {

    @NonNull
    String value;
}
