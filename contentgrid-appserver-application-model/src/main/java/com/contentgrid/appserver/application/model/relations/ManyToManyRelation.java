package com.contentgrid.appserver.application.model.relations;

import com.contentgrid.appserver.application.model.exceptions.InvalidRelationException;
import java.util.Objects;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public class ManyToManyRelation extends Relation {

    @Builder
    ManyToManyRelation(@NonNull RelationEndPoint source, @NonNull RelationEndPoint target, @NonNull String joinTable,
            @NonNull String sourceReference,
            @NonNull String targetReference) {
        super(source, target);
        if (Objects.equals(sourceReference, targetReference)) {
            throw new InvalidRelationException("'%s' is used for sourceReference and targetReference".formatted(sourceReference));
        }
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
