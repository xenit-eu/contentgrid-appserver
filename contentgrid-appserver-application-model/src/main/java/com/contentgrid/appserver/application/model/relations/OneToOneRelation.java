package com.contentgrid.appserver.application.model.relations;

import com.contentgrid.appserver.application.model.values.ColumnName;
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

    /**
     * Constructs a OneToOneRelation with the specified parameters.
     *
     * @param source the source endpoint of the relation
     * @param target the target endpoint of the relation
     * @param targetReference the column in the source entity that references the target entity
     * @throws InvalidRelationException if source and target have the same name on the same entity
     */
    @Builder
    OneToOneRelation(@NonNull RelationEndPoint source, @NonNull RelationEndPoint target, ColumnName targetReference) {
        super(source, target);
        this.targetReference = targetReference == null ? this.source.getName().toColumnName() : targetReference;
    }

    /**
     * The column in the source entity that references the target entity.
     */
    @NonNull
    ColumnName targetReference;

}
