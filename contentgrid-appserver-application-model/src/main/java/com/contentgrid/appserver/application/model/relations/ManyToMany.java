package com.contentgrid.appserver.application.model.relations;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public class ManyToMany extends Relation {

    @Builder
    ManyToMany(@NonNull RelationEndPoint source, @NonNull RelationEndPoint target, @NonNull String joinTable,
            @NonNull String sourceReference,
            @NonNull String targetReference) {
        super(source, target);
        this.joinTable = joinTable;
        this.sourceReference = sourceReference;
        this.targetReference = targetReference;
    }

    @NonNull
    String joinTable;

    @NonNull
    String sourceReference;

    @NonNull
    String targetReference;

}
