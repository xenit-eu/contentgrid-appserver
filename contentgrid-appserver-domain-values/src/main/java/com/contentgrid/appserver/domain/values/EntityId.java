package com.contentgrid.appserver.domain.values;

import java.util.UUID;
import lombok.NonNull;
import lombok.Value;

@Value(staticConstructor = "of")
public class EntityId {

    @NonNull
    UUID value;

    @Override
    public String toString() {
        return value.toString();
    }
}
