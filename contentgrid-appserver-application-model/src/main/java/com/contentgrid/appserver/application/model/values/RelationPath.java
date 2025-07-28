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
    public String toString() {
        return "%s.%s".formatted(relation, rest);
    }
}
