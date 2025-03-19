package com.contentgrid.appserver.application.model.relations;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public class ManyToOneRelation extends Relation {

    @Builder
    ManyToOneRelation(@NonNull RelationEndPoint source, @NonNull RelationEndPoint target, @NonNull String targetReference) {
        super(source, target);
        this.targetReference = targetReference;
    }

    @NonNull
    String targetReference;

}
