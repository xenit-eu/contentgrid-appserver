package com.contentgrid.appserver.application.model.values;

import lombok.NonNull;
import lombok.Value;

@Value
public class SimpleAttributePath implements AttributePath {
    @NonNull AttributeName attribute;

    @Override
    public @NonNull AttributeName getFirst() {
        return attribute;
    }

    @Override
    public AttributePath getRest() {
        return null;
    }

    @Override
    public AttributePath withSuffix(AttributeName attributeName) {
        return new CompositeAttributePath(attribute, new SimpleAttributePath(attributeName));
    }

    @Override
    public String toString() {
        return attribute.toString();
    }
}
