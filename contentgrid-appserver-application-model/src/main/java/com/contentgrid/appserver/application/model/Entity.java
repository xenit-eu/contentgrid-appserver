package com.contentgrid.appserver.application.model;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.exceptions.DuplicateElementException;
import com.contentgrid.appserver.application.model.exceptions.InvalidArgumentModelException;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.SearchFilter;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.TableName;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
public class Entity {

    /**
     * Constructs an Entity with the specified parameters.
     *
     * @param name the entity name
     * @param table the database table name
     * @param attributes list of attributes for this entity (excluding primary key attribute)
     * @param primaryKey the primary key attribute (defaults to UUID "id" if null)
     * @param searchFilters list of search filters for this entity
     * @throws DuplicateElementException if duplicate attributes or search filters are found
     * @throws InvalidArgumentModelException if a search filter references an invalid attribute
     */
    @Builder
    Entity(@NonNull EntityName name, String description, @NonNull TableName table, @Singular List<Attribute> attributes,
            SimpleAttribute primaryKey, @Singular List<SearchFilter> searchFilters) {
        this.name = name;
        this.description = description;
        this.table = table;
        this.primaryKey = primaryKey == null ? SimpleAttribute.builder().name(AttributeName.of("id")).column(ColumnName.of("id")).type(Type.UUID).build() : primaryKey;

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

        searchFilters.forEach(
                searchFilter -> {
                    if (this.searchFilters.put(searchFilter.getName(), searchFilter) != null) {
                        throw new DuplicateElementException(
                                "Duplicate search filter named %s".formatted(searchFilter.getName()));
                    }
                    if (searchFilter instanceof AttributeSearchFilter attributeSearchFilter && !attributes.contains(
                            attributeSearchFilter.getAttribute())) {
                        throw new InvalidArgumentModelException(
                                "AttributeSearchFilter %s is does not have a valid attribute".formatted(
                                        attributeSearchFilter.getName()));
                    }
                }
        );
    }

    /**
     * The name of the entity.
     */
    @NonNull
    EntityName name;

    String description;

    /**
     * The name of the database table that this entity maps to.
     */
    @NonNull
    TableName table;

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
     * Returns an unmodifiable list of attributes.
     *
     * @return an unmodifiable list of attributes
     */
    public List<Attribute> getAttributes() {
        return List.copyOf(attributes.values());
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

}
