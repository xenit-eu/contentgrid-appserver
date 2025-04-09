package com.contentgrid.appserver.application.model.relations;

import com.contentgrid.appserver.application.model.exceptions.InvalidRelationException;
import com.contentgrid.appserver.application.model.values.ColumnName;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
public class SourceOneToOneRelation extends OneToOneRelation {

    /**
     * Constructs a SourceOneToOneRelation with the specified parameters.
     *
     * @param sourceEndPoint the source endpoint of the relation
     * @param targetEndPoint the target endpoint of the relation
     * @param targetReference the column in the source entity that references the target entity
     * @throws InvalidRelationException if source and target have the same name on the same entity
     */
    @Builder
    SourceOneToOneRelation(@NonNull Relation.RelationEndPoint sourceEndPoint, @NonNull Relation.RelationEndPoint targetEndPoint, @NonNull ColumnName targetReference) {
        super(sourceEndPoint, targetEndPoint);
        if (this.getTargetEndPoint().isRequired()) {
            throw new InvalidRelationException(
                    """
                    Target endpoint %s can not be required, because the column is present on the source entity.
                    Use com.contentgrid.appserver.application.model.relations.TargetOneToOneRelation instead.
                    """.formatted(this.getTargetEndPoint().getName())
            );
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
        return TargetOneToOneRelation.builder()
                .sourceEndPoint(this.getTargetEndPoint())
                .targetEndPoint(this.getSourceEndPoint())
                .sourceReference(this.targetReference)
                .build();
    }
}
