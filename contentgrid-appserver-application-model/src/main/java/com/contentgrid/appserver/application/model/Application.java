package com.contentgrid.appserver.application.model;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.exceptions.DuplicateElementException;
import com.contentgrid.appserver.application.model.exceptions.EntityNotFoundException;
import com.contentgrid.appserver.application.model.exceptions.InvalidArgumentModelException;
import com.contentgrid.appserver.application.model.exceptions.RelationNotFoundException;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.AttributePath;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.PropertyName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.RelationPath;
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
        var linkNames = new HashSet<LinkName>();
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
            if (!linkNames.add(entity.getLinkName())) {
                throw new DuplicateElementException("Duplicate link relation named %s".formatted(entity.getLinkName()));
            }
        });

        relations.forEach(relation -> {
            if (!this.entities.containsKey(relation.getSourceEndPoint().getEntity().getName())) {
                throw new EntityNotFoundException("Source %s is not a valid entity".formatted(relation.getSourceEndPoint().getEntity().getName()));
            }
            if (!this.entities.containsKey(relation.getTargetEndPoint().getEntity().getName())) {
                throw new EntityNotFoundException("Source %s is not a valid entity".formatted(relation.getTargetEndPoint().getEntity().getName()));
            }
            if (this.relations.stream().filter(relation::collides).count() > 1) {
                throw new DuplicateElementException("Duplicate relation on entity %s named %s".formatted(relation.getSourceEndPoint().getEntity().getName(), relation.getSourceEndPoint().getName()));
            }
            if (relation instanceof ManyToManyRelation manyToManyRelation && !tables.add(manyToManyRelation.getJoinTable())) {
                throw new DuplicateElementException("Duplicate table named %s".formatted(manyToManyRelation.getJoinTable()));
            }
        });

        // Validating entity search filters (happens here rather than in Entity because they might go across relations)
        this.entities.values().forEach(this::validateEntitySearchFilters);
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

    public Entity getRequiredEntityByName(EntityName entityName) throws EntityNotFoundException {
        return getEntityByName(entityName).orElseThrow(() ->
                new EntityNotFoundException("Entity %s not found".formatted(entityName)));
    }

    public Optional<Entity> getEntityByPathSegment(PathSegmentName pathSegment) {
        return Optional.ofNullable(pathSegmentEntities.get(pathSegment));
    }

    /**
     * Finds a relation for a given Entity and relation name.
     *
     * @param entityName the name of the entity containing the relation
     * @param relationName the relation name to match
     * @return an Optional containing a relation where the entity is either the source or target entity
     * and the name matches
     */
    public Optional<Relation> getRelationForEntity(EntityName entityName, RelationName relationName) {
        for (var relation : relations) {
            if (relation.getSourceEndPoint().getEntity().getName().equals(entityName) && Objects.equals(relation.getSourceEndPoint().getName(), relationName)) {
                return Optional.of(relation);
            } else if (relation.getTargetEndPoint().getEntity().getName().equals(entityName) && Objects.equals(relation.getTargetEndPoint().getName(), relationName)) {
                return Optional.of(relation.inverse());
            }
        }
        return Optional.empty();
    }

    /**
     * Finds a relation for a given entity name and relation name.
     *
     * @param entity the entity containing the relation
     * @param name the relation name to match
     * @return an optional containing a relation where the entity is either the source or target entity
     * and the name matches
     */
    public Optional<Relation> getRelationForEntity(Entity entity, RelationName name) {
        return getRelationForEntity(entity.getName(), name);
    }

    public Relation getRequiredRelationForEntity(Entity entity, RelationName name) {
        return getRelationForEntity(entity, name).orElseThrow(() ->
                new RelationNotFoundException("Relation %s not found on entity %s".formatted(name, entity.getName())));
    }

    public Relation getRequiredRelationForEntity(EntityName entityName, RelationName name) {
        return getRelationForEntity(entityName, name).orElseThrow(() ->
                new RelationNotFoundException("Relation %s not found on entity %s".formatted(name, entityName)));
    }

    public Optional<Relation> getRelationForPath(PathSegmentName entitySegment, PathSegmentName relationSegment) {
        for (var relation : relations) {
            if (relation.getSourceEndPoint().getEntity().getPathSegment().equals(entitySegment) && Objects.equals(relation.getSourceEndPoint().getPathSegment(), relationSegment)) {
                return Optional.of(relation);
            } else if (relation.getTargetEndPoint().getEntity().getPathSegment().equals(entitySegment) && Objects.equals(relation.getTargetEndPoint().getPathSegment(), relationSegment)) {
                return Optional.of(relation.inverse());
            }
        }
        return Optional.empty();
    }

    /**
     * Finds all relations with the given source entity.
     * <p>
     * If the source entity equals the target entity of a relation,
     * both the relation and its inverse relation will be present.
     *
     * @param entity the source entity
     * @return a Set containing all the relations where the entity is the source entity
     */
    public Set<Relation> getRelationsForSourceEntity(Entity entity) {
        var results = new HashSet<Relation>();
        for (var relation : relations) {
            if (relation.getSourceEndPoint().getEntity().equals(entity)) {
                results.add(relation);
            }
            if (relation.getTargetEndPoint().getEntity().equals(entity)) {
                results.add(relation.inverse());
            }
        }
        return results;
    }

    /**
     * Finds all relations with the given target entity.
     * <p>
     * If the source entity equals the target entity of a relation,
     * both the relation and its inverse relation will be present.
     *
     * @param entity the target entity
     * @return a Set containing all the relations where the entity is the target entity
     */
    public Set<Relation> getRelationsForTargetEntity(Entity entity) {
        var results = new HashSet<Relation>();
        for (var relation : relations) {
            if (relation.getTargetEndPoint().getEntity().equals(entity)) {
                results.add(relation);
            }
            if (relation.getSourceEndPoint().getEntity().equals(entity)) {
                results.add(relation.inverse());
            }
        }
        return results;
    }


    private void validateEntitySearchFilters(Entity entity) {
        entity.getSearchFilters().forEach(searchFilter -> {
            if (searchFilter instanceof AttributeSearchFilter attributeSearchFilter) {
                try {
                    var resolvedAttribute = resolvePropertyPath(entity, attributeSearchFilter.getAttributePath());
                    if (resolvedAttribute.getType() != attributeSearchFilter.getAttributeType()) {
                        throw new InvalidArgumentModelException(
                            "SearchFilter %s does not match the type of attribute %s (%s != %s)".formatted(
                                    attributeSearchFilter.getName(), resolvedAttribute.getName(),
                                    attributeSearchFilter.getAttributeType(), resolvedAttribute.getType()));
                    }
                } catch (IllegalArgumentException e) {
                    throw new InvalidArgumentModelException("SearchFilter %s does not have a valid attribute path"
                            .formatted(attributeSearchFilter.getName()), e);
                }
            }
        });
    }

    private SimpleAttribute resolvePropertyPath(Entity entity, PropertyPath path) {
        Entity currentEntity = entity;
        PropertyPath currentPath = path;

        while (currentPath != null) {
            switch (currentPath) {
                case AttributePath attributePath -> {
                    // When we hit an attribute, validate the remaining path via the current entity
                    return currentEntity.resolveAttributePath(attributePath);
                }
                case RelationPath relationPath -> {
                    final String entityName = currentEntity.getName().getValue(); // Make final for lambda
                    var relation = getRelationForEntity(currentEntity, relationPath.getRelation())
                        .orElseThrow(() -> new IllegalArgumentException(
                            "Relation '%s' not found on entity '%s'"
                                .formatted(relationPath.getFirst().getValue(), entityName)));

                    // Move to the target entity
                    currentEntity = relation.getTargetEndPoint().getEntity();
                    currentPath = currentPath.getRest();
                }
            }
        }

        throw new IllegalArgumentException("Invalid property path: path ended without reaching an attribute");
    }

}
