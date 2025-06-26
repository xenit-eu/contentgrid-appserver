package com.contentgrid.appserver.application.model;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttributeImpl;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.exceptions.DuplicateElementException;
import com.contentgrid.appserver.application.model.exceptions.InvalidArgumentModelException;
import com.contentgrid.appserver.application.model.exceptions.InvalidAttributeTypeException;
import com.contentgrid.appserver.application.model.searchfilters.SearchFilter;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.AttributePath;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.CompositeAttributePath;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.SimpleAttributePath;
import com.contentgrid.appserver.application.model.values.TableName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

/**
 * Represents an entity within an application.
 * 
 * An Entity is a model that maps to a database table and contains attributes, a primary key,
 * and search filters. It provides methods to access and manage its attributes and search filters.
 * 
 * @see Entity.EntityBuilder
 */
@Value
public class Entity implements HasAttributes {

    /**
     * Constructs an Entity with the specified parameters.
     *
     * @param name the entity name
     * @param pathSegment the url path segment
     * @param description the description for this entity
     * @param table the database table name
     * @param linkName the link name used in link relation 'cg:entity'
     * @param attributes list of attributes for this entity (excluding primary key attribute)
     * @param primaryKey the primary key attribute (defaults to UUID "id" if null)
     * @param searchFilters list of search filters for this entity
     * @throws DuplicateElementException if duplicate attributes or search filters are found
     * @throws InvalidArgumentModelException if a search filter references an invalid attribute
     * @throws InvalidAttributeTypeException if primary key has an invalid type
     */
    @Builder
    Entity(
            @NonNull EntityName name,
            @NonNull PathSegmentName pathSegment,
            String description,
            @NonNull TableName table,
            @NonNull LinkName linkName,
            @Singular List<Attribute> attributes,
            SimpleAttribute primaryKey,
            @Singular List<SearchFilter> searchFilters
    ) {
        this.name = name;
        this.pathSegment = pathSegment;
        this.description = description;
        this.table = table;
        this.linkName = linkName;
        if (primaryKey == null) {
            this.primaryKey = SimpleAttribute.builder().name(AttributeName.of("id")).column(ColumnName.of("id")).type(Type.UUID).build();
        } else if (Type.UUID.equals(primaryKey.getType())) {
            this.primaryKey = primaryKey;
        } else {
            throw new InvalidAttributeTypeException("Type %s is not supported for primary key".formatted(primaryKey.getType()));
        }

        this.attributes.put(this.primaryKey.getName(), this.primaryKey);
        var columns = new HashSet<ColumnName>();
        columns.add(this.primaryKey.getColumn());

        attributes.forEach(
                attribute -> {
                    if (this.attributes.put(attribute.getName(), attribute) != null) {
                        throw new DuplicateElementException(
                                "Duplicate attribute named %s".formatted(attribute.getName()));
                    }
                    attribute.getColumns().forEach(column -> {
                        if (!columns.add(column)) {
                            throw new DuplicateElementException(
                                    "Duplicate column named %s".formatted(column));
                        }
                    });
                }
        );

        var contentLinks = new HashSet<LinkName>();

        getContentAttributes(attributes).forEach(attribute -> {
            if (this.contentAttributes.put(attribute.getPathSegment(), attribute) != null) {
                throw new DuplicateElementException("Duplicate content with path segment %s".formatted(attribute.getPathSegment()));
            }
            if (!contentLinks.add(attribute.getLinkName())) {
                throw new DuplicateElementException("Duplicate content with link name %s".formatted(attribute.getLinkName()));
            }
        });

        searchFilters.forEach(
                searchFilter -> {
                    if (this.searchFilters.put(searchFilter.getName(), searchFilter) != null) {
                        throw new DuplicateElementException(
                                "Duplicate search filter named %s".formatted(searchFilter.getName()));
                    }
                }
        );
        this.attributes.remove(this.primaryKey.getName());
    }

    /**
     * The name of the entity.
     */
    @NonNull
    EntityName name;

    @NonNull
    PathSegmentName pathSegment;

    String description;

    /**
     * The name of the database table that this entity maps to.
     */
    @NonNull
    TableName table;

    @NonNull
    LinkName linkName;

    /**
     * The primary key attribute of this entity.
     */
    @NonNull
    SimpleAttribute primaryKey;

    /**
     * Internal map of attributes by name.
     */
    @Getter(AccessLevel.NONE)
    Map<AttributeName, Attribute> attributes = new HashMap<>();

    /**
     * Internal map of search filters by name.
     */
    @Getter(AccessLevel.NONE)
    Map<FilterName, SearchFilter> searchFilters = new HashMap<>();

    /**
     * Internal map of content attributes by path segment.
     */
    @Getter(AccessLevel.NONE)
    Map<PathSegmentName, ContentAttribute> contentAttributes = new HashMap<>();

    /**
     * Returns an unmodifiable list of attributes (primary key excluded).
     *
     * @return an unmodifiable list of attributes (primary key excluded)
     */
    public List<Attribute> getAttributes() {
        return List.copyOf(attributes.values());
    }

    /**
     * Returns an unmodifiable list of attributes (primary key included).
     *
     * @return an unmodifiable list of attributes (primary key included)
     */
    public List<Attribute> getAllAttributes() {
        return Stream.concat(Stream.of(this.primaryKey), attributes.values().stream()).toList();
    }

    /**
     * Returns an unmodifiable list of content attributes.
     * Includes content attributes that are nested under CompositeAttributeImpl.
     *
     * @return an unmodifiable list of content attributes
     */
    public List<ContentAttribute> getContentAttributes() {
        return List.copyOf(contentAttributes.values());
    }

    /**
     * Returns an unmodifiable list of search filters.
     * @return an unmodifiable list of search filters
     */
    public List<SearchFilter> getSearchFilters() {
        return List.copyOf(searchFilters.values());
    }

    /**
     * Finds an Attribute by its name.
     *
     * @param attributeName the name of the attribute to find
     * @return an Optional containing the Attribute if found, or empty if not found
     */
    public Optional<Attribute> getAttributeByName(AttributeName attributeName) {
        if (this.primaryKey.getName().equals(attributeName)) {
            return Optional.of(this.primaryKey);
        }
        return Optional.ofNullable(attributes.get(attributeName));
    }

    /**
     * Finds a SearchFilter by its name.
     *
     * @param filterName the name of the filter to find
     * @return an Optional containing the SearchFilter if found, or empty if not found
     */
    public Optional<SearchFilter> getFilterByName(FilterName filterName) {
        return Optional.ofNullable(searchFilters.get(filterName));
    }

    /**
     * Finds a content attribute by its path segment name.
     * Is also able to find content attributes that are nested under CompositeAttributeImpl.
     *
     * @param pathSegment the path segment name of the content to find
     * @return an Optional containing the content attribute if found, or empty if not found
     */
    public Optional<ContentAttribute> getContentByPathSegment(PathSegmentName pathSegment) {
        return Optional.ofNullable(contentAttributes.get(pathSegment));
    }

    private static List<ContentAttribute> getContentAttributes(List<Attribute> attributes) {
        var result = new ArrayList<ContentAttribute>();
        for (var attribute : attributes) {
            if (attribute instanceof ContentAttribute contentAttribute) {
                result.add(contentAttribute);
            }
            if (attribute instanceof CompositeAttributeImpl compositeAttribute) {
                result.addAll(getContentAttributes(compositeAttribute.getAttributes()));
            }
        }
        return result;
    }

    public SimpleAttribute resolveAttributePath(@NonNull AttributePath attributePath) {
        return resolveAttributePath(this, attributePath);
    }

    private static SimpleAttribute resolveAttributePath(@NonNull HasAttributes container, @NonNull AttributePath attributePath) {
        return switch (attributePath) {
            case SimpleAttributePath simpleAttributePath -> {
                var attr = container.getAttributeByName(simpleAttributePath.getFirst())
                        .orElseThrow(() -> new IllegalArgumentException("Attribute not found: " + simpleAttributePath.getFirst()));
                if (attr instanceof SimpleAttribute simpleAttribute) {
                    yield simpleAttribute;
                }
                throw new IllegalArgumentException("SimpleAttributePath didn't end up at SimpleAttribute: " + attributePath);
            }
            case CompositeAttributePath compositeAttributePath -> {
                var attr = container.getAttributeByName(compositeAttributePath.getFirst())
                        .orElseThrow(() -> new IllegalArgumentException("Attribute not found: " + compositeAttributePath.getFirst()));
                if (attr instanceof CompositeAttribute compAttribute) {
                    yield resolveAttributePath(compAttribute, compositeAttributePath.getRest());
                }
                throw new IllegalArgumentException("CompositeAttributePath goes over SimpleAttribute: " + attributePath);
            }
        };
    }
}
