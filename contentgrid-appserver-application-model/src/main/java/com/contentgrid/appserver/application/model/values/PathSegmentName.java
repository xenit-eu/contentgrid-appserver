package com.contentgrid.appserver.application.model.values;

import lombok.NonNull;
import lombok.Value;

@Value(staticConstructor = "of")
public class PathSegmentName {

    @NonNull
    String value;

    @Override
    public String toString() {
        return getValue();
    }
}
