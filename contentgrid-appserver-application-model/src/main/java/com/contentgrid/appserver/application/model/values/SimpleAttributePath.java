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
}
