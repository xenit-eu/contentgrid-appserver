package com.contentgrid.appserver.application.model.openapi.type;

import com.contentgrid.appserver.application.model.values.EntityName;
import lombok.NonNull;
import lombok.Value;

@Value
public class EntityType implements SemanticType {
    @NonNull
    EntityName entityName;
}
