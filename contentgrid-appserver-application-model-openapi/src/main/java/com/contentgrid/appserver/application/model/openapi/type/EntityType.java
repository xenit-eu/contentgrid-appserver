package com.contentgrid.appserver.application.model.openapi.type;

import com.contentgrid.appserver.application.model.values.EntityName;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@NonFinal
public sealed class EntityType implements SemanticType permits RelationItemType{
    @NonNull
    EntityName entityName;
}
