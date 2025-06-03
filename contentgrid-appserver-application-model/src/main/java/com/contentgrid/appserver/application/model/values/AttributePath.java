package com.contentgrid.appserver.application.model.values;

import lombok.NonNull;

public sealed interface AttributePath extends PropertyPath permits SimpleAttributePath, CompositeAttributePath {
    @NonNull AttributeName getFirst();
    AttributePath getRest();
}
