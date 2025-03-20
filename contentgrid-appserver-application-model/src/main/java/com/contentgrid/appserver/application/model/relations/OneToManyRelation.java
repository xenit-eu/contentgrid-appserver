package com.contentgrid.appserver.application.model.relations;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;

/**
 * Represents a one-to-many relationship between two entities.
 * 
 * In a one-to-many relationship, one entity (the source) can be associated with multiple
 * instances of another entity (the target), but a target entity can only be associated
 * with one source entity.
 */
@EqualsAndHashCode(callSuper = true)
@Value
public class OneToManyRelation extends Relation {

    @Builder
    OneToManyRelation(@NonNull RelationEndPoint source, @NonNull RelationEndPoint target, @NonNull String sourceReference) {
        super(source, target);
        this.sourceReference = sourceReference;
    }

    /**
     * The column in the target entity that references the source entity.
     */
    @NonNull
    String sourceReference;

}
