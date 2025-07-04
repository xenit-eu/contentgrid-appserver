package com.contentgrid.appserver.application.model.relations;

import lombok.NonNull;

/**
 * Represents a one-to-one relationship between two entities.
 *
 * In a one-to-one relationship, one instance of an entity (the source) can be associated with
 * at most one instance of another entity (the target), and vice versa.
 */
public abstract sealed class OneToOneRelation extends Relation permits SourceOneToOneRelation, TargetOneToOneRelation {

    protected OneToOneRelation(@NonNull RelationEndPoint sourceEndPoint, @NonNull RelationEndPoint targetEndPoint) {
        super(sourceEndPoint, targetEndPoint);
    }

    @Override
    public abstract OneToOneRelation inverse();
}
