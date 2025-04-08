package com.contentgrid.appserver.application.model.relations;

import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.exceptions.InvalidRelationException;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.RelationName;
import java.util.Objects;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;

/**
 * Represents a relationship between two entities in the application model.
 * 
 * The Relation class is the base class for all types of relationships between entities.
 * It defines source and target endpoints that specify the related entities and their relation names.
 */
@Getter
@EqualsAndHashCode
public abstract sealed class Relation permits ManyToManyRelation, ManyToOneRelation, OneToManyRelation,
        OneToOneRelation {

    protected Relation(@NonNull RelationEndPoint source, @NonNull RelationEndPoint target) {
        if (source.getName() == null && target.getName() == null) {
            throw new InvalidRelationException("At least one endpoint must have a name");
        }
        if (source.getPathSegment() == null && target.getPathSegment() == null) {
            throw new InvalidRelationException("At least one endpoint must have a path segment");
        }
        if ((source.getName() == null && source.getPathSegment() != null) ||
                (source.getName() != null && source.getPathSegment() == null)) {
            throw new InvalidRelationException("Name and path segment of source endpoint must be both absent or both present");
        }
        if ((target.getName() == null && target.getPathSegment() != null) ||
                (target.getName() != null && target.getPathSegment() == null)) {
            throw new InvalidRelationException("Name and path segment of target endpoint must be both absent or both present");
        }
        if (source.getEntity().getName().equals(target.getEntity().getName())
                && Objects.equals(source.getName(), target.getName())) {
            throw new InvalidRelationException("Source and target must have a different name when on the same entity");
        }
        if (source.getEntity().getPathSegment().equals(target.getEntity().getPathSegment())
                && Objects.equals(source.getPathSegment(), target.getPathSegment())) {
            throw new InvalidRelationException("Source and target must have a different path segment when on the same entity");
        }
        if (source.getName() == null && source.isRequired()) {
            throw new InvalidRelationException("Source is required but doesn't have a name");
        }
        if (target.getName() == null && target.isRequired()) {
            throw new InvalidRelationException("Target is required but doesn't have a name");
        }
        if (source.isRequired() && target.isRequired()) {
            // Chicken and egg problem
            throw new InvalidRelationException("Source and target can not be both required");
        }
        this.source = source;
        this.target = target;
    }

    /**
     * The source endpoint of the relation.
     */
    @NonNull
    RelationEndPoint source;

    /**
     * The target endpoint of the relation.
     */
    @NonNull
    RelationEndPoint target;


    /**
     * Represents an endpoint of a relation, defining an entity and an optional relation name.
     */
    @Value
    @Builder
    public static class RelationEndPoint {

        /**
         * The name of this relation endpoint from the perspective of the entity.
         */
        RelationName name;

        PathSegmentName pathSegment;

        String description;

        /**
         * The entity at this endpoint of the relation.
         */
        @NonNull
        Entity entity;

        boolean required;
    }

    public abstract Relation inverse();

    /**
     * Returns whether this relation collides with the other relation.
     *
     * @param other The relation to check
     * @return whether this relation collides with the other relation.
     */
    public boolean collides(Relation other) {
        return collidesName(other) || collidesSegment(other);
    }

    private boolean collidesName(Relation other) {
        var sourceName = this.getSource().getName();
        var sourceEntity = this.getSource().getEntity().getName();
        var targetName = this.getTarget().getName();
        var targetEntity = this.getTarget().getEntity().getName();

        var otherSourceName = other.getSource().getName();
        var otherSourceEntity = other.getSource().getEntity().getName();
        var otherTargetName = other.getTarget().getName();
        var otherTargetEntity = other.getTarget().getEntity().getName();

        return (sourceName != null && Objects.equals(sourceName, otherSourceName) && Objects.equals(sourceEntity, otherSourceEntity))
                ||
                (targetName != null && Objects.equals(targetName, otherTargetName) && Objects.equals(targetEntity, otherTargetEntity))
                ||
                (sourceName != null && Objects.equals(sourceName, otherTargetName) && Objects.equals(sourceEntity, otherTargetEntity))
                ||
                (targetName != null && Objects.equals(targetName, otherSourceName) && Objects.equals(targetEntity, otherSourceEntity));
    }

    /**
     * Returns whether the url path segment of this relation collides with the segment of the other relation.
     *
     * @param other The relation to check
     * @return whether the url path segment of this relation collides with the segment of the other relation.
     */
    private boolean collidesSegment(Relation other) {
        var sourceName = this.getSource().getPathSegment();
        var sourceEntity = this.getSource().getEntity().getPathSegment();
        var targetName = this.getTarget().getPathSegment();
        var targetEntity = this.getTarget().getEntity().getPathSegment();

        var otherSourceName = other.getSource().getPathSegment();
        var otherSourceEntity = other.getSource().getEntity().getPathSegment();
        var otherTargetName = other.getTarget().getPathSegment();
        var otherTargetEntity = other.getTarget().getEntity().getPathSegment();

        return (sourceName != null && Objects.equals(sourceName, otherSourceName) && Objects.equals(sourceEntity, otherSourceEntity))
                ||
                (targetName != null && Objects.equals(targetName, otherTargetName) && Objects.equals(targetEntity, otherTargetEntity))
                ||
                (sourceName != null && Objects.equals(sourceName, otherTargetName) && Objects.equals(sourceEntity, otherTargetEntity))
                ||
                (targetName != null && Objects.equals(targetName, otherSourceName) && Objects.equals(targetEntity, otherSourceEntity));
    }


}
