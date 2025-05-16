package com.contentgrid.appserver.query.engine.api.data;

import java.util.UUID;
import lombok.NonNull;
import lombok.Value;

@Value(staticConstructor = "of")
public class EntityId {

    @NonNull
    UUID value;
}
