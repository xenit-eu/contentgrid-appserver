package com.contentgrid.appserver.application.model.relations;

import com.contentgrid.appserver.application.model.exceptions.InvalidRelationException;
import com.contentgrid.appserver.application.model.values.ColumnName;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
public class TargetOneToOneRelation extends OneToOneRelation {

    /**
     * Constructs a TargetOneToOneRelation with the specified parameters.
     *
     * @param sourceEndPoint the source endpoint of the relation
     * @param targetEndPoint the target endpoint of the relation
     * @param sourceReference the column in the target entity that references the source entity
     * @throws InvalidRelationException if source and target have the same name on the same entity
     */
    @Builder
    TargetOneToOneRelation(@NonNull Relation.RelationEndPoint sourceEndPoint, @NonNull Relation.RelationEndPoint targetEndPoint, @NonNull ColumnName sourceReference) {
        super(sourceEndPoint, targetEndPoint);
        if (this.getSourceEndPoint().isRequired()) {
            throw new InvalidRelationException(
                    """
                    Source endpoint %s can not be required, because the column is present on the target entity.
                    Use com.contentgrid.appserver.application.model.relations.SourceOneToOneRelation instead.
                    """.formatted(this.getSourceEndPoint().getName())
            );
        }
        this.sourceReference = sourceReference;
    }

    /**
     * The column in the target entity that references the source entity.
     */
    @NonNull
    ColumnName sourceReference;

    @Override
    public SourceOneToOneRelation inverse() {
        return SourceOneToOneRelation.builder()
                .sourceEndPoint(this.getTargetEndPoint())
                .targetEndPoint(this.getSourceEndPoint())
                .targetReference(this.sourceReference)
                .build();
    }
}
