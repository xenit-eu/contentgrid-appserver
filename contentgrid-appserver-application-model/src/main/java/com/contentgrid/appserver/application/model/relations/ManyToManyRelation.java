package com.contentgrid.appserver.application.model.relations;

import com.contentgrid.appserver.application.model.exceptions.InvalidRelationException;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.TableName;
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

    /**
     * Constructs a ManyToManyRelation with the specified parameters.
     *
     * @param source the source endpoint of the relation
     * @param target the target endpoint of the relation
     * @param joinTable the name of the join table
     * @param sourceReference the column in the join table referencing the source entity
     * @param targetReference the column in the join table referencing the target entity
     * @throws InvalidRelationException if source and target have the same name on the same entity, or
     *                                 if sourceReference and targetReference are the same
     */
    @Builder
    ManyToManyRelation(@NonNull RelationEndPoint source, @NonNull RelationEndPoint target, @NonNull TableName joinTable,
            @NonNull ColumnName sourceReference,
            @NonNull ColumnName targetReference) {
        super(source, target);
        if (Objects.equals(sourceReference, targetReference)) {
            throw new InvalidRelationException("'%s' is used for sourceReference and targetReference".formatted(sourceReference));
        }
        if (this.getTarget().isRequired()) {
            throw new InvalidRelationException("Target endpoint %s can not be required, because it does not reference a single source entity".formatted(this.getTarget().getName()));
        }
        if (this.getSource().isRequired()) {
            throw new InvalidRelationException("Source endpoint %s can not be required, because it does not reference a single target entity".formatted(this.getSource().getName()));
        }
        this.joinTable = joinTable;
        this.sourceReference = sourceReference;
        this.targetReference = targetReference;
    }

    /**
     * The name of the join table that implements the many-to-many relationship.
     */
    @NonNull
    TableName joinTable;

    /**
     * The column in the join table that references the source entity.
     */
    @NonNull
    ColumnName sourceReference;

    /**
     * The column in the join table that references the target entity.
     */
    @NonNull
    ColumnName targetReference;

    @Override
    public Relation inverse() {
        return ManyToManyRelation.builder()
                .source(this.getTarget())
                .target(this.getSource())
                .joinTable(this.joinTable)
                .sourceReference(this.targetReference)
                .targetReference(this.sourceReference)
                .build();
    }
}
