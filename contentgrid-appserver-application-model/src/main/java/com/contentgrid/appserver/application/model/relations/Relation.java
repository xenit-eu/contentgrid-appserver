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

    protected Relation(@NonNull RelationEndPoint sourceEndPoint, @NonNull RelationEndPoint targetEndPoint) {
        if (sourceEndPoint.getName() == null && targetEndPoint.getName() == null) {
            throw new InvalidRelationException("At least one endpoint must have a name");
        }
        if (sourceEndPoint.getEntity().getName().equals(targetEndPoint.getEntity().getName())
                && Objects.equals(sourceEndPoint.getName(), targetEndPoint.getName())) {
            throw new InvalidRelationException("Source and target endpoints must have a different name when on the same entity");
        }
        if (sourceEndPoint.getEntity().getPathSegment().equals(targetEndPoint.getEntity().getPathSegment())
                && Objects.equals(sourceEndPoint.getPathSegment(), targetEndPoint.getPathSegment())) {
            throw new InvalidRelationException("Source and target endpoints must have a different path segment when on the same entity");
        }
        if (sourceEndPoint.isRequired() && targetEndPoint.isRequired()) {
            // Chicken and egg problem
            throw new InvalidRelationException("Source and target endpoints can not be both required");
        }
        if (sourceEndPoint.getEntity().getName().equals(targetEndPoint.getEntity().getName())
                && (sourceEndPoint.isRequired() || targetEndPoint.isRequired())) {
            // Chicken and egg problem
            throw new InvalidRelationException("Source and target endpoints can not be required when on the same entity");
        }
        this.sourceEndPoint = sourceEndPoint;
        this.targetEndPoint = targetEndPoint;
    }

    /**
     * The source endpoint of the relation.
     */
    @NonNull
    RelationEndPoint sourceEndPoint;

    /**
     * The target endpoint of the relation.
     */
    @NonNull
    RelationEndPoint targetEndPoint;


    /**
     * Represents an endpoint of a relation, defining an entity and an optional relation name.
     */
    @Value
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

        @Builder
        RelationEndPoint(@NonNull Entity entity, RelationName name, PathSegmentName pathSegment, String description, boolean required) {
            if (name != null && pathSegment == null) {
                throw new InvalidRelationException("Relation endpoint with name %s does not have a pathSegment".formatted(name));
            }
            if (name == null && pathSegment != null) {
                throw new InvalidRelationException("Relation endpoint with pathSegment %s does not have a name".formatted(pathSegment));
            }
            if (name == null && required) {
                throw new InvalidRelationException("Relation endpoint can not be required without name");
            }
            this.entity = entity;
            this.name = name;
            this.pathSegment = pathSegment;
            this.description = description;
            this.required = required;
        }
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
        var sourceName = this.getSourceEndPoint().getName();
        var sourceEntity = this.getSourceEndPoint().getEntity().getName();
        var targetName = this.getTargetEndPoint().getName();
        var targetEntity = this.getTargetEndPoint().getEntity().getName();

        var otherSourceName = other.getSourceEndPoint().getName();
        var otherSourceEntity = other.getSourceEndPoint().getEntity().getName();
        var otherTargetName = other.getTargetEndPoint().getName();
        var otherTargetEntity = other.getTargetEndPoint().getEntity().getName();

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
        var sourceName = this.getSourceEndPoint().getPathSegment();
        var sourceEntity = this.getSourceEndPoint().getEntity().getPathSegment();
        var targetName = this.getTargetEndPoint().getPathSegment();
        var targetEntity = this.getTargetEndPoint().getEntity().getPathSegment();

        var otherSourceName = other.getSourceEndPoint().getPathSegment();
        var otherSourceEntity = other.getSourceEndPoint().getEntity().getPathSegment();
        var otherTargetName = other.getTargetEndPoint().getPathSegment();
        var otherTargetEntity = other.getTargetEndPoint().getEntity().getPathSegment();

        return (sourceName != null && Objects.equals(sourceName, otherSourceName) && Objects.equals(sourceEntity, otherSourceEntity))
                ||
                (targetName != null && Objects.equals(targetName, otherTargetName) && Objects.equals(targetEntity, otherTargetEntity))
                ||
                (sourceName != null && Objects.equals(sourceName, otherTargetName) && Objects.equals(sourceEntity, otherTargetEntity))
                ||
                (targetName != null && Objects.equals(targetName, otherSourceName) && Objects.equals(targetEntity, otherSourceEntity));
    }


}
