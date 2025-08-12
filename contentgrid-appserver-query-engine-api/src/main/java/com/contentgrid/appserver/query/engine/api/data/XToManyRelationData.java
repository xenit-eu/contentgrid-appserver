package com.contentgrid.appserver.query.engine.api.data;

import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.domain.values.EntityId;
import java.util.Set;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class XToManyRelationData implements RelationData {

    @NonNull
    RelationName name;

    @Singular
    Set<@NonNull EntityId> refs;
}
