package com.contentgrid.appserver.application.model.propertypath;

import com.contentgrid.appserver.application.model.values.AttributeName;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

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
    public AttributeName getLast() {
        return getFirst();
    }

    @Override
    public AttributePath withSuffix(AttributeName attributeName) {
        return new CompositeAttributePath(attribute, new SimpleAttributePath(attributeName));
    }

    @Override
    public List<AttributePath> getAsList() {
        return List.of(this);
    }

    @Override
    public String toString() {
        return attribute.toString();
    }
}
