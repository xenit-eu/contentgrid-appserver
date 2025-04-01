package com.contentgrid.appserver.application.model.relations;

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
     * @param source the source endpoint of the relation
     * @param target the target endpoint of the relation
     * @param targetReference the column in the source entity that references the target entity
     * @throws InvalidRelationException if source and target have the same name on the same entity
     */
    @Builder
    SourceOneToOneRelation(@NonNull Relation.RelationEndPoint source, @NonNull Relation.RelationEndPoint target, @NonNull ColumnName targetReference) {
        super(source, target);
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
                .source(this.getTarget())
                .target(this.getSource())
                .sourceReference(this.targetReference)
                .build();
    }
}
