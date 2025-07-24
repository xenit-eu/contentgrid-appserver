package com.contentgrid.appserver.query.engine.api.data;

import com.contentgrid.appserver.application.model.values.RelationName;
import lombok.NonNull;

public sealed interface RelationData permits XToOneRelationData, XToManyRelationData  {

    @NonNull
    RelationName getName();
}
