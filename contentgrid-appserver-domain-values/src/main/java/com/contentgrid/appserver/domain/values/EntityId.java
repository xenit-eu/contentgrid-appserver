package com.contentgrid.appserver.domain.values;

import java.io.Serializable;
import java.util.UUID;
import lombok.NonNull;
import lombok.Value;

@Value(staticConstructor = "of")
public class EntityId implements Serializable {

    @NonNull
    UUID value;

    @Override
    public String toString() {
        return value.toString();
    }
}
