package com.contentgrid.appserver.application.model.relations;

import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.exceptions.InvalidRelationException;
import com.contentgrid.appserver.application.model.values.LinkName;
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
        if (sourceEndPoint.collides(targetEndPoint)) {
            throw new InvalidRelationException("Source and target endpoints must not collide when on the same entity");
        }
        if (sourceEndPoint.isRequired() && targetEndPoint.isRequired()) {
            // Chicken and egg problem
            throw new InvalidRelationException("Source and target endpoints can not be both required");
        }
        if (sourceEndPoint.getEntity().getName().equals(targetEndPoint.getEntity().getName())
                && (sourceEndPoint.isRequired() || targetEndPoint.isRequired())) {
            // Chicken and egg problem
            throw new InvalidRelationException("Source or target endpoint can not be required when on the same entity");
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

        /**
         * The link name of this relation endpoint.
         * This value is used as the name property in the 'cg:relation' link relation.
         */
        LinkName linkName;

        String description;

        /**
         * The entity at this endpoint of the relation.
         */
        @NonNull
        Entity entity;

        boolean required;

        @Builder
        RelationEndPoint(@NonNull Entity entity, RelationName name, PathSegmentName pathSegment, LinkName linkName, String description, boolean required) {
            if (name != null && pathSegment == null) {
                throw new InvalidRelationException("Relation endpoint with name %s does not have a pathSegment".formatted(name));
            }
            if (name == null && pathSegment != null) {
                throw new InvalidRelationException("Relation endpoint with pathSegment %s does not have a name".formatted(pathSegment));
            }
            if (name == null && description != null) {
                throw new InvalidRelationException("Relation endpoint can not have a description without a name");
            }
            if (name == null && required) {
                throw new InvalidRelationException("Relation endpoint can not be required without name");
            }
            if (pathSegment == null && linkName != null) {
                throw new InvalidRelationException("Relation endpoint with linkName %s does not have a pathSegment".formatted(
                        linkName));
            }
            if (pathSegment != null && linkName == null) {
                throw new InvalidRelationException("Relation endpoint with pathSegment %s does not have a linkName".formatted(pathSegment));
            }
            this.entity = entity;
            this.name = name;
            this.pathSegment = pathSegment;
            this.linkName = linkName;
            this.description = description;
            this.required = required;
        }

        /**
         * Returns whether this relation endpoint collides with the other relation endpoint.
         *
         * @param other The relation endpoint to check
         * @return whether this relation endpoint collides with the other relation endpoint.
         */
        public boolean collides(RelationEndPoint other) {
            return (
                    // Check name
                    this.name != null
                            && Objects.equals(this.entity.getName(), other.entity.getName())
                            && Objects.equals(this.name, other.name)
            ) || (
                    // Check pathSegment
                    this.pathSegment != null
                            && Objects.equals(this.entity.getPathSegment(), other.entity.getPathSegment())
                            && Objects.equals(this.pathSegment, other.pathSegment)
            ) || (
                    // Check linkName
                    this.linkName != null
                            && Objects.equals(this.entity.getName(), other.entity.getName())
                            && Objects.equals(this.linkName, other.linkName)
            );
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
        return this.getSourceEndPoint().collides(other.getSourceEndPoint())
                || this.getSourceEndPoint().collides(other.getTargetEndPoint())
                || this.getTargetEndPoint().collides(other.getSourceEndPoint())
                || this.getTargetEndPoint().collides(other.getTargetEndPoint());
    }

}
