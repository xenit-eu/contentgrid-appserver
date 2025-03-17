package com.contentgrid.appserver.application.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

@Value
public class Application {

    @Builder
    Application(String name, @Singular Set<Entity> entities, @Singular Set<Relation> relations) {
        this.name = name;
        this.relations = relations;
        entities.forEach(entity -> {
            if (this.entities.put(entity.getName(), entity) != null) {
                throw new IllegalArgumentException("Duplicate entity named %s".formatted(entity.getName()));
            }
        });

        relations.forEach(relation -> {
            if (!this.entities.containsValue(relation.getSource().getEntity())) {
                throw new IllegalArgumentException("Source %s is not a valid entity".formatted(relation.getSource().getEntity().getName()));
            }
            if (!this.entities.containsValue(relation.getTarget().getEntity())) {
                throw new IllegalArgumentException("Source %s is not a valid entity".formatted(relation.getTarget().getEntity().getName()));
            }
        });
    }

    String name;

    Map<String, Entity> entities = new HashMap<>();

    Set<Relation> relations;

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
