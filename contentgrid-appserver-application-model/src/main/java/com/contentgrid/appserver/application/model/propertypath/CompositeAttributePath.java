package com.contentgrid.appserver.application.model.propertypath;

import com.contentgrid.appserver.application.model.values.AttributeName;
import java.util.ArrayList;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * An attribute path that crosses attributes and resolves to an attribute
 */
@RequiredArgsConstructor
@EqualsAndHashCode
public final class CompositeAttributePath implements AttributePath {
    @NonNull
    AttributeName attribute;
    @Getter
    @NonNull
    AttributePath rest;

    @Override
    public @NonNull AttributeName getFirst() {
        return attribute;
    }

    @Override
    public AttributeName getLast() {
        return getRest().getLast();
    }

    @Override
    public AttributePath withSuffix(AttributeName attributeName) {
        return new CompositeAttributePath(attribute, rest.withSuffix(attributeName));
    }

    @Override
    public List<AttributePath> getAsList() {
        var result = new ArrayList<AttributePath>();
        result.add(new SimpleAttributePath(getFirst()));
        for (var currentRest = getRest(); currentRest != null ; currentRest = currentRest.getRest()) {
            result.add(result.getLast().withSuffix(currentRest.getFirst()));
        }
        return result;
    }

    @Override
    public String toString() {
        return "%s.%s".formatted(attribute, rest);
    }
}
