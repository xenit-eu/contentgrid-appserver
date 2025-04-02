package com.contentgrid.appserver.application.model;

import com.contentgrid.appserver.application.model.exceptions.DuplicateElementException;
import com.contentgrid.appserver.application.model.exceptions.EntityNotFoundException;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.TableName;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

/**
 * Represents an application in the ContentGrid platform.
 * 
 * An Application is the top-level entity that contains entities and their relationships.
 * It provides methods to access and manage entities and relations within the application.
 * 
 * @see Application.ApplicationBuilder
 */
@Value
public class Application {

    /**
     * Constructs an Application with the specified parameters.
     *
     * @param name the application name
     * @param entities set of entities within this application
     * @param relations set of relations between entities
     * @throws DuplicateElementException if duplicate entities are found
     * @throws EntityNotFoundException if a relation references an entity not in the application
     */
    @Builder
    Application(@NonNull ApplicationName name, @Singular Set<Entity> entities, @Singular Set<Relation> relations) {
        this.name = name;
        this.relations = relations;
        var tables = new HashSet<TableName>();
        entities.forEach(entity -> {
            if (this.entities.put(entity.getName(), entity) != null) {
                throw new DuplicateElementException("Duplicate entity named %s".formatted(entity.getName()));
            }
            if (!tables.add(entity.getTable())) {
                throw new DuplicateElementException("Duplicate table named %s".formatted(entity.getTable()));
            }
            if (this.pathSegmentEntities.put(entity.getPathSegment(), entity) != null) {
                throw new DuplicateElementException("Duplicate path segment named %s".formatted(entity.getPathSegment()));
            }
        });

        relations.forEach(relation -> {
            if (!this.entities.containsValue(relation.getSource().getEntity())) {
                throw new EntityNotFoundException("Source %s is not a valid entity".formatted(relation.getSource().getEntity().getName()));
            }
            if (!this.entities.containsValue(relation.getTarget().getEntity())) {
                throw new EntityNotFoundException("Source %s is not a valid entity".formatted(relation.getTarget().getEntity().getName()));
            }
            if (this.relations.stream().filter(relation::collides).count() > 1) {
                throw new DuplicateElementException("Duplicate relation on entity %s named %s".formatted(relation.getSource().getEntity().getName(), relation.getSource().getName()));
            }
        });
    }

    /**
     * The name of the application.
     */
    @NonNull
    ApplicationName name;

    /**
     * Internal map of entities by name.
     */
    @Getter(AccessLevel.NONE)
    Map<EntityName, Entity> entities = new HashMap<>();

    @Getter(AccessLevel.NONE)
    Map<PathSegmentName, Entity> pathSegmentEntities = new HashMap<>();

    /**
     * The set of relations defined in this application.
     */
    Set<Relation> relations;

    /**
     * Returns an unmodifiable set of entities.
     * @return an unmodifiable set of entities
     */
    public Set<Entity> getEntities() {
        return Set.copyOf(entities.values());
    }

    /**
     * Finds an Entity by its name.
     *
     * @param entityName the name of the entity to find
     * @return an Optional containing the Entity if found, or empty if not found
     */
    public Optional<Entity> getEntityByName(EntityName entityName) {
        return Optional.ofNullable(entities.get(entityName));
    }

    public Optional<Entity> getEntityByPathSegment(PathSegmentName pathSegment) {
        return Optional.ofNullable(pathSegmentEntities.get(pathSegment));
    }

    /**
     * Finds a relation for a given Entity and relation name.
     *
     * @param entity the entity containing the relation
     * @param name the relation name to match
     * @return an Optional containing a relation where the entity is either the source or target entity
     * and the name matches
     */
    public Optional<Relation> getRelationForEntity(Entity entity, RelationName name) {
        for (var relation : relations) {
            if (relation.getSource().getEntity().equals(entity) && Objects.equals(relation.getSource().getName(), name)) {
                return Optional.of(relation);
            } else if (relation.getTarget().getEntity().equals(entity) && Objects.equals(relation.getTarget().getName(), name)) {
                return Optional.of(relation.inverse());
            }
        }
        return Optional.empty();
    }

    /**
     * Finds a relation for a given entity name and relation name.
     *
     * @param entityName the name of the entity containing the relation
     * @param name the relation name to match
     * @return an optional containing a relation where the entity is either the source or target entity
     * and the name matches
     */
    public Optional<Relation> getRelationForEntity(EntityName entityName, RelationName name) {
        return getEntityByName(entityName).flatMap(entity -> getRelationForEntity(entity, name));
    }

    public Optional<Relation> getRelationForPath(PathSegmentName entitySegment, PathSegmentName relationSegment) {
        for (var relation : relations) {
            if (relation.getSource().getEntity().getPathSegment().equals(entitySegment) && Objects.equals(relation.getSource().getPathSegment(), relationSegment)) {
                return Optional.of(relation);
            } else if (relation.getTarget().getEntity().getPathSegment().equals(entitySegment) && Objects.equals(relation.getTarget().getPathSegment(), relationSegment)) {
                return Optional.of(relation.inverse());
            }
        }
        return Optional.empty();
    }

}
