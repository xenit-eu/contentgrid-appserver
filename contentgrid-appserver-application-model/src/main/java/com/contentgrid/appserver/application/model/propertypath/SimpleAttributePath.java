package com.contentgrid.appserver.application.model.propertypath;

import com.contentgrid.appserver.application.model.values.AttributeName;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * Attribute path that crosses only a single attribute name
 */
@RequiredArgsConstructor
@EqualsAndHashCode
public final class SimpleAttributePath implements AttributePath {
    @NonNull
    AttributeName attribute;

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
