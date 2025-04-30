package com.contentgrid.appserver.query.engine.api.data;

import com.contentgrid.appserver.application.model.values.RelationName;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class XToOneRelationData<T> implements RelationData {

    @NonNull
    RelationName name;

    T ref;
}
