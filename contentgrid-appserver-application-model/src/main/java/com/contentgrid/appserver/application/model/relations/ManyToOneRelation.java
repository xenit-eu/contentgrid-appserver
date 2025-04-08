package com.contentgrid.appserver.application.model.relations;

import com.contentgrid.appserver.application.model.exceptions.InvalidRelationException;
import com.contentgrid.appserver.application.model.values.ColumnName;
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

    /**
     * Constructs a ManyToOneRelation with the specified parameters.
     *
     * @param source the source endpoint of the relation
     * @param target the target endpoint of the relation
     * @param targetReference the column in the source entity that references the target entity
     * @throws InvalidRelationException if source and target have the same name on the same entity
     */
    @Builder
    ManyToOneRelation(@NonNull RelationEndPoint source, @NonNull RelationEndPoint target, @NonNull ColumnName targetReference) {
        super(source, target);
        if (this.getTargetEndPoint().isRequired()) {
            throw new InvalidRelationException("Target endpoint %s can not be required, because it does not reference a single source entity".formatted(this.getTargetEndPoint().getName()));
        }
        this.targetReference = targetReference;
    }

    /**
     * The column in the source entity that references the target entity.
     */
    @NonNull
    ColumnName targetReference;

    @Override
    public Relation inverse() {
        return OneToManyRelation.builder()
                .source(this.getTargetEndPoint())
                .target(this.getSourceEndPoint())
                .sourceReference(this.targetReference)
                .build();
    }
}
