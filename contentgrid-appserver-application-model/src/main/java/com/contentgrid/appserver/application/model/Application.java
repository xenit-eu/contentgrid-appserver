package com.contentgrid.appserver.application.model;

import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.propertypath.InvalidPropertyPathException;
import com.contentgrid.appserver.application.model.propertypath.PropertyPath.ResolvesToAttribute;
import com.contentgrid.appserver.application.model.settings.ApplicationSettings;
import com.contentgrid.appserver.application.model.exceptions.DuplicateElementException;
import com.contentgrid.appserver.application.model.exceptions.EntityDefinitionNotFoundException;
import com.contentgrid.appserver.application.model.exceptions.InvalidArgumentModelException;
import com.contentgrid.appserver.application.model.exceptions.InvalidEntityLinkException;
import com.contentgrid.appserver.application.model.exceptions.InvalidSearchFilterException;
import com.contentgrid.appserver.application.model.exceptions.RelationNotFoundException;
import com.contentgrid.appserver.application.model.exceptions.SearchFilterNotFoundException;
import com.contentgrid.appserver.application.model.links.UriTemplateDefinition;
import com.contentgrid.appserver.application.model.links.UriTemplateDefinition.EntityLinkSubstitutionVariables;
import com.contentgrid.appserver.application.model.propertypath.CompositeRelationPath;
import com.contentgrid.appserver.application.model.propertypath.PropertyPathResolver;
import com.contentgrid.appserver.application.model.propertypath.SimpleAttributePath;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.relations.flags.HiddenEndpointFlag;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter.Operation;
import com.contentgrid.appserver.application.model.searchfilters.SearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.flags.SyntheticSearchFilterFlag;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.propertypath.PropertyPath;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.hateoas.uritemplate.InvalidUriTemplateException;
import com.contentgrid.hateoas.uritemplate.ParameterizedUriTemplateParser;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
     * @param settings application-specific settings
     * @throws DuplicateElementException if duplicate entities are found
     * @throws EntityDefinitionNotFoundException if a relation references an entity not in the application
     */
    @Builder
    Application(@NonNull ApplicationName name, @Singular Set<Entity> entities, @Singular Set<Relation> relations,
            ApplicationSettings settings) {
        this.name = name;
        this.settings = settings == null ? ApplicationSettings.builder().build() : settings;
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
            if (!this.entities.containsKey(relation.getSourceEndPoint().getEntity())) {
                throw new EntityDefinitionNotFoundException("Source %s is not a valid entity".formatted(relation.getSourceEndPoint().getEntity()));
            }
            if (!this.entities.containsKey(relation.getTargetEndPoint().getEntity())) {
                throw new EntityDefinitionNotFoundException("Target %s is not a valid entity".formatted(relation.getTargetEndPoint().getEntity()));
            }
            if (this.relations.stream().anyMatch(relation::collides)) {
                throw new DuplicateElementException("Duplicate relation on entity %s named %s".formatted(relation.getSourceEndPoint().getEntity(), relation.getSourceEndPoint().getName()));
            }
            if (relation instanceof ManyToManyRelation manyToManyRelation && !tables.add(manyToManyRelation.getJoinTable())) {
                throw new DuplicateElementException("Duplicate table named %s".formatted(manyToManyRelation.getJoinTable()));
            }
            ensureRequiredRelationFiltersPresent(relation);
            ensureRequiredRelationFiltersPresent(relation.inverse());
            this.relations.add(relation);
        });

        // Validating entity search filters (happens here rather than in Entity because they might go across relations)
        this.entities.values().forEach(this::validateEntitySearchFilters);

        // Validating entity links (happens here rather than in Entity or EntityLink because they might go to a relation)
        this.entities.values().forEach(this::validateEntityLinks);
    }

    /**
     * The name of the application.
     */
    @NonNull
    ApplicationName name;

    @NonNull
    ApplicationSettings settings;

    /**
     * Internal map of entities by name.
     */
    @Getter(AccessLevel.NONE)
    Map<EntityName, Entity> entities = new LinkedHashMap<>();

    @Getter(AccessLevel.NONE)
    Map<PathSegmentName, Entity> pathSegmentEntities = new LinkedHashMap<>();

    /**
     * Internal set of relations defined in this application.
     */
    @Getter(AccessLevel.NONE)
    Set<Relation> relations = new LinkedHashSet<>();

    PropertyPathResolver propertyPathResolver = new PropertyPathResolver(this);

    /**
     * Returns an unmodifiable set of relations.
     * @return an unmodifiable set of relations
     */
    public Set<Relation> getRelations() {
        return Collections.unmodifiableSet(relations);
    }

    /**
     * Returns an unmodifiable list of entities.
     * @return an unmodifiable list of entities
     */
    public List<Entity> getEntities() {
        return List.copyOf(entities.values());
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

    public Entity getRequiredEntityByName(EntityName entityName) throws EntityDefinitionNotFoundException {
        return getEntityByName(entityName).orElseThrow(() ->
                new EntityDefinitionNotFoundException(entityName));
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
            if (relation.getSourceEndPoint().getEntity().equals(entityName) && Objects.equals(relation.getSourceEndPoint().getName(), relationName)) {
                return Optional.of(relation);
            } else if (relation.getTargetEndPoint().getEntity().equals(entityName) && Objects.equals(relation.getTargetEndPoint().getName(), relationName)) {
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
        return getEntityByPathSegment(entitySegment)
                .map(entity -> {
                    for (var relation : relations) {
                        if (relation.getSourceEndPoint().getEntity().equals(entity.getName())
                                && Objects.equals(relation.getSourceEndPoint().getPathSegment(), relationSegment)) {
                            return relation;
                        } else if (relation.getTargetEndPoint().getEntity().equals(entity.getName())
                                && Objects.equals(relation.getTargetEndPoint().getPathSegment(), relationSegment)) {
                            return relation.inverse();
                        }
                    }
                    return null;
                });
    }

    public Entity getRelationSourceEntity(Relation relation) {
        return getRequiredEntityByName(relation.getSourceEndPoint().getEntity());
    }

    public Entity getRelationTargetEntity(Relation relation) {
        return getRequiredEntityByName(relation.getTargetEndPoint().getEntity());
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
        var results = new LinkedHashSet<Relation>();
        for (var relation : relations) {
            if (relation.getSourceEndPoint().getEntity().equals(entity.getName())) {
                results.add(relation);
            }
            if (relation.getTargetEndPoint().getEntity().equals(entity.getName())) {
                results.add(relation.inverse());
            }
        }
        // TODO: Should we filter down this list to only consider relations that have a name on the source side?
        // It is a recipe for NPEs to sometimes suddenly receive an entity that has no name/path segment/...
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
        var results = new LinkedHashSet<Relation>();
        for (var relation : relations) {
            if (relation.getTargetEndPoint().getEntity().equals(entity.getName())) {
                results.add(relation);
            }
            if (relation.getSourceEndPoint().getEntity().equals(entity.getName())) {
                results.add(relation.inverse());
            }
        }
        // TODO: These are incoming relations. Should we also filter down this list to only consider relations that have a name on the source side?
        // This function is currently unused, but I can imagine it being used for collection filters
        return results;
    }

    /**
     * Find the filter that is used for the given relation redirect.
     * <p>
     * The filter is defined on the target entity. And is used for one-to-many and many-to-many relation redirects.
     *
     * @param relation the one-to-many or many-to-many relation
     * @return the filter on the target entity belonging to the given relation
     */
    public SearchFilter getFilterForRelation(Relation relation) {
        return findFilterForRelation(relation)
                .orElseThrow(() -> new SearchFilterNotFoundException("No search filter found on %s for relation %s".formatted(
                        relation.getTargetEndPoint().getEntity(),
                        relation.getSourceEndPoint().getName()
                )));
    }

    private Optional<AttributeSearchFilter> findFilterForRelation(Relation relation) {
        var sourceEntity = getRelationSourceEntity(relation);
        var targetEntity = getRelationTargetEntity(relation);
        var propertyPath = PropertyPath.of(relation.getTargetEndPoint().getName(), sourceEntity.getPrimaryKey().getName());
        return targetEntity.getSearchFilters().stream()
                .filter(AttributeSearchFilter.class::isInstance)
                .map(AttributeSearchFilter.class::cast)
                .filter(filter ->
                        Operation.EXACT == filter.getOperation() && propertyPath.equals(filter.getAttributePath()))
                .findFirst();
    }

    /**
     * Ensures that the relation filters that are necessary for the application to function are present
     * <p>
     * For to-many relations, we need a filter on the target entity collection, so we can redirect from the
     * relation endpoint to the collection with that filter present
     */
    private void ensureRequiredRelationFiltersPresent(Relation relation) {
        if (relation.getSourceEndPoint().hasFlag(HiddenEndpointFlag.class)) {
            // Hidden relations don't need a filter, because they can't be followed
            return;
        }
        if (!(relation instanceof OneToManyRelation || relation instanceof ManyToManyRelation)) {
            // Ony to-many relations need a filter to redirect to; to-one relations redirect directly to their target
            return;
        }

        if (findFilterForRelation(relation).isPresent()) {
            // The relation already has a filter; we don't need to create a synthetic one
            return;
        }

        var sourceEntity = getRelationSourceEntity(relation);
        var targetEntity = getRelationTargetEntity(relation);

        // Generate a filter name in case the original is already occupied
        var originalFilterName = "__internal_%s".formatted(relation.getTargetEndPoint().getName().getValue());
        var filterName = FilterName.of(originalFilterName);
        int i = 0;
        while(targetEntity.getFilterByName(filterName).isPresent()) {
            i++;
            filterName = FilterName.of(originalFilterName+"_"+i);
        }

        var filter = AttributeSearchFilter.builder()
                .name(filterName)
                .attributePath(CompositeRelationPath.of(relation.getTargetEndPoint().getName(), new SimpleAttributePath(sourceEntity.getPrimaryKey().getName())))
                .flag(SyntheticSearchFilterFlag.INSTANCE)
                .operation(Operation.EXACT)
                .build();

        var newTargetEntity = targetEntity.withAdditionalSearchFilters(Set.of(filter));

        entities.put(targetEntity.getName(), newTargetEntity);
        pathSegmentEntities.put(targetEntity.getPathSegment(), newTargetEntity);
    }

    private void validateEntitySearchFilters(Entity entity) {
        entity.getSearchFilters().forEach(searchFilter -> {
            if (searchFilter instanceof AttributeSearchFilter attributeSearchFilter) {
                    var resolvedAttribute = resolvePropertyPath(entity, attributeSearchFilter.getAttributePath());
                    if(!attributeSearchFilter.supports(resolvedAttribute)) {
                        throw new InvalidSearchFilterException(
                            "SearchFilter %s does not support the attribute %s".formatted(
                                    attributeSearchFilter.getName(), resolvedAttribute
                            ));
                    }
            }
        });
    }

    private void validateEntityLinks(Entity entity) {
        entity.getLinks().forEach(link -> {
                    var supportedVariables = EnumSet.allOf(EntityLinkSubstitutionVariables.class);
                    // These are always available
                    supportedVariables.add(EntityLinkSubstitutionVariables.APPLICATION_ID);
                    supportedVariables.add(EntityLinkSubstitutionVariables.ENTITY_ID);
                    supportedVariables.add(EntityLinkSubstitutionVariables.ENTITY_LINK);
                    supportedVariables.add(EntityLinkSubstitutionVariables.ENTITY_NAME);

                    if (link.getOwner() != null) {
                        supportedVariables.add(EntityLinkSubstitutionVariables.OWNER_NAME);
                        switch (getPropertyPathResolver().resolve(entity.getName(), link.getOwner())) {
                            case AttributeResolutionResult attrResult when attrResult.getAttribute() instanceof SimpleAttribute:
                                // Simple attributes have an owner value
                                supportedVariables.add(EntityLinkSubstitutionVariables.OWNER_VALUE);
                                break;
                            case AttributeResolutionResult attributeResolutionResult when attributeResolutionResult.getAttribute() instanceof ContentAttribute:
                                // Content attributes have a link
                                supportedVariables.add(EntityLinkSubstitutionVariables.OWNER_LINK);
                                break;
                            case RelationResolutionResult relationResolutionResult:
                                // Relations have a link
                                supportedVariables.add(EntityLinkSubstitutionVariables.OWNER_LINK);
                                break;
                            default:
                                throw new InvalidEntityLinkException("Entity link owner property path '%s' on entity '%s' does not reference a supported type".formatted(link.getOwner(), entity.getName()));
                        }
                    }

                    if (link.getStorage() != null) {
                        throw new InvalidEntityLinkException("Entity links with storage are not supported");
                    }

                    if(link.getFallbackTemplate() != null) {
                        var parser = new ParameterizedUriTemplateParser<>(supportedVariables);
                        try {
                            parser.parse(link.getFallbackTemplate().getTemplate().toTemplate());
                        } catch (InvalidUriTemplateException e) {
                            throw new InvalidEntityLinkException("Entity link fallback template references an unsupported substitution variable", e);
                        }
                    }

                });


    }

    /**
     * @deprecated use the {@link #getPropertyPathResolver()} instead
     */
    @Deprecated(forRemoval = true, since = "0.1.1")
    public SimpleAttribute resolvePropertyPath(Entity entity, PropertyPath path) {
        try {
            var attributeResult = propertyPathResolver.resolveAttribute(entity.getName(), path.as(ResolvesToAttribute.class));
            if (attributeResult.getAttribute() instanceof SimpleAttribute simpleAttribute) {
                return simpleAttribute;
            }
        } catch (InvalidPropertyPathException e) {
            throw new InvalidArgumentModelException("Resolving path '%s' against entity '%s' did not reach a SimpleAttribute".formatted(path, entity.getName()), e);
        }
        throw new InvalidArgumentModelException("Resolving path '%s' against entity '%s' did not reach a SimpleAttribute".formatted(path, entity.getName()));
    }

}
