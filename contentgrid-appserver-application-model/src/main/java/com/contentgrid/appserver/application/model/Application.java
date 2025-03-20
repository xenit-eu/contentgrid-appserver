package com.contentgrid.appserver.application.model;

import com.contentgrid.appserver.application.model.exceptions.DuplicateElementException;
import com.contentgrid.appserver.application.model.exceptions.EntityNotFoundException;
import com.contentgrid.appserver.application.model.relations.Relation;
import java.util.HashMap;
import java.util.Map;
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
    Application(@NonNull String name, @Singular Set<Entity> entities, @Singular Set<Relation> relations) {
        this.name = name;
        this.relations = relations;
        entities.forEach(entity -> {
            if (this.entities.put(entity.getName(), entity) != null) {
                throw new DuplicateElementException("Duplicate entity named %s".formatted(entity.getName()));
            }
            if (this.tableEntities.put(entity.getTable(), entity) != null) {
                throw new DuplicateElementException("Duplicate table named %s".formatted(entity.getTable()));
            }
        });

        relations.forEach(relation -> {
            if (!this.entities.containsValue(relation.getSource().getEntity())) {
                throw new EntityNotFoundException("Source %s is not a valid entity".formatted(relation.getSource().getEntity().getName()));
            }
            if (!this.entities.containsValue(relation.getTarget().getEntity())) {
                throw new EntityNotFoundException("Source %s is not a valid entity".formatted(relation.getTarget().getEntity().getName()));
            }
        });
    }

    /**
     * The name of the application.
     */
    @NonNull
    String name;

    /**
     * Internal map of entities by name.
     */
    @Getter(AccessLevel.NONE)
    Map<String, Entity> entities = new HashMap<>();

    /**
     * Internal map of entities by table name.
     */
    @Getter(AccessLevel.NONE)
    Map<String, Entity> tableEntities = new HashMap<>();

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
     * Finds an Entity by the table name.
     * @param table name of the table
     * @return an Optional containing the Entity if found, or empty if not found
     */
    public Optional<Entity> getEntityByTable(String table) {
        return Optional.ofNullable(tableEntities.get(table));
    }

    /**
     * Finds an Entity by its name.
     *
     * @param entityName the name of the entity to find
     * @return an Optional containing the Entity if found, or empty if not found
     */
    public Optional<Entity> getEntityByName(String entityName) {
        return Optional.ofNullable(entities.get(entityName));
    }

    /**
     * Finds all relations for a given Entity and relation name.
     *
     * @param entity the entity to find relations for
     * @param name the relation name to match
     * @return a list of relations where the entity is either the left or right entity and the name matches
     */
    public Optional<Relation> getRelationForEntity(Entity entity, String name) {
        return relations.stream()
                .filter(relation ->
                        (relation.getSource().getEntity().equals(entity) && relation.getSource().getName().equals(name))
                                ||
                                (relation.getTarget().getEntity().equals(entity) && relation.getTarget().getName()
                                        .equals(name)))
                .findFirst();
    }


}
