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


}
