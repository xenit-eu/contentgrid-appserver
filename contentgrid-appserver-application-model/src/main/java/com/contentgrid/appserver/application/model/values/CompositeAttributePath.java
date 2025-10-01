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
    public AttributePath withSuffix(AttributeName attributeName) {
        return new CompositeAttributePath(attribute, rest.withSuffix(attributeName));
    }

    @Override
    public String toString() {
        return "%s.%s".formatted(attribute, rest);
    }
}
