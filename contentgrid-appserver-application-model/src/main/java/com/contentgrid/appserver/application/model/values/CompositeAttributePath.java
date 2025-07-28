package com.contentgrid.appserver.application.model.values;

import lombok.NonNull;
import lombok.Value;

@Value
public class CompositeAttributePath implements AttributePath {
    @NonNull AttributeName attribute;
    @NonNull AttributePath rest;

    @Override
    public @NonNull AttributeName getFirst() {
        return attribute;
    }

    @Override
    public String toString() {
        return "%s.%s".formatted(attribute, rest);
    }
}
