package com.contentgrid.appserver.query.engine.api.data;

import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.RelationName;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class XToOneRelationData<T> implements RelationData {

    @NonNull
    EntityName entity;

    @NonNull
    RelationName name;

    T ref;
}
