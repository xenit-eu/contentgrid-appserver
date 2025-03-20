package com.contentgrid.appserver.application.model.relations;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;

/**
 * Represents a one-to-one relationship between two entities.
 * 
 * In a one-to-one relationship, one instance of an entity (the source) can be associated with
 * at most one instance of another entity (the target), and vice versa.
 */
@EqualsAndHashCode(callSuper = true)
@Value
public class OneToOneRelation extends Relation {

    @Builder
    OneToOneRelation(@NonNull RelationEndPoint source, @NonNull RelationEndPoint target, @NonNull String targetReference) {
        super(source, target);
        this.targetReference = targetReference;
    }

    /**
     * The column in the source entity that references the target entity.
     */
    @NonNull
    String targetReference;

}
