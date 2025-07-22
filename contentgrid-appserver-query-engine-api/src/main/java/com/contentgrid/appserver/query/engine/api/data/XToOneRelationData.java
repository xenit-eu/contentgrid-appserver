package com.contentgrid.appserver.query.engine.api.data;

import com.contentgrid.appserver.application.model.values.RelationName;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class XToOneRelationData implements RelationData {

    @NonNull
    RelationName name;

    @NonNull
    EntityId ref;
}
