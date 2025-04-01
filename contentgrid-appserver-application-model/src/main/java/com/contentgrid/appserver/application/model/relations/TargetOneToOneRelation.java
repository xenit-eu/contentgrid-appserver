package com.contentgrid.appserver.application.model.relations;

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
     * @param source the source endpoint of the relation
     * @param target the target endpoint of the relation
     * @param sourceReference the column in the target entity that references the source entity
     * @throws InvalidRelationException if source and target have the same name on the same entity
     */
    @Builder
    TargetOneToOneRelation(@NonNull Relation.RelationEndPoint source, @NonNull Relation.RelationEndPoint target, @NonNull ColumnName sourceReference) {
        super(source, target);
        this.sourceReference = sourceReference;
    }

    /**
     * The column in the target entity that references the source entity.
     */
    @NonNull
    ColumnName sourceReference;

    @Override
    public Relation inverse() {
        return SourceOneToOneRelation.builder()
                .source(this.getTarget())
                .target(this.getSource())
                .targetReference(this.sourceReference)
                .build();
    }
}
