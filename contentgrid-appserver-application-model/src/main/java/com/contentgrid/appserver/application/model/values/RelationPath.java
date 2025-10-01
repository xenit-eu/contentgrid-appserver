package com.contentgrid.appserver.application.model.values;

import lombok.NonNull;
import lombok.Value;

@Value
public class RelationPath implements PropertyPath {
    @NonNull RelationName relation;
    PropertyPath rest;

    @Override
    public @NonNull PropertyName getFirst() {
        return relation;
    }

    @Override
    public RelationPath withSuffix(AttributeName attributeName) {
        return new RelationPath(relation, rest.withSuffix(attributeName));
    }

    @Override
    public AttributePath withPrefix(AttributeName attributeName) {
        throw new IllegalStateException("Can not prefix RelationPath %s with AttributeName %s".formatted(this, attributeName));
    }

    @Override
    public String toString() {
        return "%s.%s".formatted(relation, rest);
    }
}
