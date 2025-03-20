package com.contentgrid.appserver.application.model.relations;

import com.contentgrid.appserver.application.model.exceptions.InvalidRelationException;
import java.util.Objects;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;

/**
 * Represents a many-to-many relationship between two entities.
 * 
 * In a many-to-many relationship, multiple instances of one entity (the source) can be associated
 * with multiple instances of another entity (the target). This is implemented using a join table
 * that contains references to both entities.
 */
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

    /**
     * The name of the join table that implements the many-to-many relationship.
     */
    @NonNull
    String joinTable;

    /**
     * The column in the join table that references the source entity.
     */
    @NonNull
    String sourceReference;

    /**
     * The column in the join table that references the target entity.
     */
    @NonNull
    String targetReference;

}
