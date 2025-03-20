package com.contentgrid.appserver.application.model.relations;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;

/**
 * Represents a many-to-one relationship between two entities.
 * 
 * In a many-to-one relationship, multiple instances of one entity (the source) can be associated
 * with a single instance of another entity (the target).
 */
@EqualsAndHashCode(callSuper = true)
@Value
public class ManyToOneRelation extends Relation {

    @Builder
    ManyToOneRelation(@NonNull RelationEndPoint source, @NonNull RelationEndPoint target, @NonNull String targetReference) {
        super(source, target);
        this.targetReference = targetReference;
    }

    /**
     * The column in the source entity that references the target entity.
     */
    @NonNull
    String targetReference;

}
