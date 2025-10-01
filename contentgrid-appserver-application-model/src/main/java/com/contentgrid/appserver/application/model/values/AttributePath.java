package com.contentgrid.appserver.application.model.values;

import lombok.NonNull;

public sealed interface AttributePath extends PropertyPath permits SimpleAttributePath, CompositeAttributePath {
    @NonNull AttributeName getFirst();
    AttributePath getRest();

    @Override
    AttributePath withSuffix(AttributeName attributeName);

    @Override
    default AttributePath withPrefix(AttributeName attributeName) {
        return new CompositeAttributePath(attributeName, this);
    }
}
