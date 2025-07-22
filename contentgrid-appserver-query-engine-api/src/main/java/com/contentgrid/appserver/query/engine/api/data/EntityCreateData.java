package com.contentgrid.appserver.query.engine.api.data;

import com.contentgrid.appserver.application.model.values.EntityName;
import java.util.Set;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

@Builder
@Value
public class EntityCreateData {
    @NonNull
    EntityName entityName;
    @Singular
    @NonNull
    Set<AttributeData> attributes;
    @Singular
    @NonNull
    Set<RelationData> relations;
}
