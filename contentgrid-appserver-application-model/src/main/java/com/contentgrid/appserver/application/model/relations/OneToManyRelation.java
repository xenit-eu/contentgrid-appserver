package com.contentgrid.appserver.application.model.relations;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public class OneToManyRelation extends Relation {

    @Builder
    OneToManyRelation(@NonNull RelationEndPoint source, @NonNull RelationEndPoint target, @NonNull String sourceReference) {
        super(source, target);
        this.sourceReference = sourceReference;
    }

    @NonNull
    String sourceReference;

}
