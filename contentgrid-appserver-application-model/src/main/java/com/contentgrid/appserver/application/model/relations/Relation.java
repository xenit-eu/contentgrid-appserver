package com.contentgrid.appserver.application.model.relations;

import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.exceptions.InvalidRelationException;
import java.util.Objects;
import lombok.Builder;
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
public abstract class Relation {

    protected Relation(@NonNull RelationEndPoint source, @NonNull RelationEndPoint target) {
        if (source.getName() == null) {
            throw new InvalidRelationException("Source endpoint must have a name");
        }
        if (source.getEntity().getName().equals(target.getEntity().getName())
                && Objects.equals(source.getName(), target.getName())) {
            throw new InvalidRelationException("Source and target must have a different name when on the same entity");
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
        String name;

        /**
         * The entity at this endpoint of the relation.
         */
        @NonNull
        Entity entity;
    }

    /**
     * Returns whether this relation collides with the other relation.
     *
     * @param other The relation to check
     * @return whether this relation collides with the other relation.
     */
    public boolean collides(Relation other) {
        var sourceName = this.getSource().getName();
        var sourceEntity = this.getSource().getEntity().getName();
        var targetName = this.getTarget().getName();
        var targetEntity = this.getTarget().getEntity().getName();

        var otherSourceName = other.getSource().getName();
        var otherSourceEntity = other.getSource().getEntity().getName();
        var otherTargetName = other.getTarget().getName();
        var otherTargetEntity = other.getTarget().getEntity().getName();

        return (Objects.equals(sourceName, otherSourceName) && Objects.equals(sourceEntity, otherSourceEntity))
                ||
                (Objects.equals(targetName, otherTargetName) && Objects.equals(targetEntity, otherTargetEntity))
                ||
                (Objects.equals(sourceName, otherTargetName) && Objects.equals(sourceEntity, otherTargetEntity))
                ||
                (Objects.equals(targetName, otherSourceName) && Objects.equals(targetEntity, otherSourceEntity));
    }


}
