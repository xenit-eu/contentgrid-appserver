package com.contentgrid.appserver.application.model.relations;

import com.contentgrid.appserver.application.model.exceptions.InvalidRelationException;
import com.contentgrid.appserver.application.model.values.ColumnName;
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

    /**
     * Constructs a OneToManyRelation with the specified parameters.
     *
     * @param source the source endpoint of the relation
     * @param target the target endpoint of the relation
     * @param sourceReference the column in the target entity that references the source entity
     * @throws InvalidRelationException if source and target have the same name on the same entity
     */
    @Builder
    OneToManyRelation(@NonNull RelationEndPoint source, @NonNull RelationEndPoint target, @NonNull ColumnName sourceReference) {
        super(source, target);
        if (this.getSourceEndPoint().isRequired()) {
            throw new InvalidRelationException("Source endpoint %s can not be required, because it does not reference a single target entity".formatted(this.getSourceEndPoint().getName()));
        }
        this.sourceReference = sourceReference;
    }

    /**
     * The column in the target entity that references the source entity.
     */
    @NonNull
    ColumnName sourceReference;

    @Override
    public Relation inverse() {
        return ManyToOneRelation.builder()
                .source(this.getTargetEndPoint())
                .target(this.getSourceEndPoint())
                .targetReference(this.sourceReference)
                .build();
    }
}
