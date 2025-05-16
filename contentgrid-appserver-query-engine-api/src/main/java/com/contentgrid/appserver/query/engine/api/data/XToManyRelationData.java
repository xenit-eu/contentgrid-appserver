package com.contentgrid.appserver.query.engine.api.data;

import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.RelationName;
import java.util.List;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class XToManyRelationData implements RelationData {

    @NonNull
    EntityName entity;

    @NonNull
    RelationName name;

    @Singular
    List<@NonNull EntityId> refs;
}
