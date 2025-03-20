package com.contentgrid.appserver.application.model;

import com.contentgrid.appserver.application.model.Attribute.Type;
import com.contentgrid.appserver.application.model.exceptions.DuplicateElementException;
import com.contentgrid.appserver.application.model.exceptions.InvalidArgumentModelException;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.SearchFilter;
import java.util.HashMap;
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
 */
@Value
public class Entity {

    @Builder
    Entity(@NonNull String name, @NonNull String table, @Singular List<Attribute> attributes,
            Attribute primaryKey, @Singular List<SearchFilter> searchFilters) {
        this.name = name;
        this.table = table;
        this.primaryKey = primaryKey == null ? Attribute.builder().name("id").column("id").type(Type.UUID).build() : primaryKey;

        this.attributes.put(this.primaryKey.getName(), this.primaryKey);
        this.columnAttributes.put(this.primaryKey.getColumn(), this.primaryKey);

        attributes.forEach(
                attribute -> {
                    if (this.attributes.put(attribute.getName(), attribute) != null) {
                        throw new DuplicateElementException(
                                "Duplicate attribute named %s".formatted(attribute.getName()));
                    }
                    if (this.columnAttributes.put(attribute.getColumn(), attribute) != null) {
                        throw new DuplicateElementException(
                                "Duplicate column named %s".formatted(attribute.getColumn()));
                    }
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
    String name;

    /**
     * The name of the database table that this entity maps to.
     */
    @NonNull
    String table;

    /**
     * The primary key attribute of this entity.
     */
    @NonNull
    Attribute primaryKey;

    /**
     * Internal map of attributes by name.
     */
    @Getter(AccessLevel.NONE)
    Map<String, Attribute> attributes = new HashMap<>();

    /**
     * Internal map of attributes by column name.
     */
    @Getter(AccessLevel.NONE)
    Map<String, Attribute> columnAttributes = new HashMap<>();

    /**
     * Internal map of search filters by name.
     */
    @Getter(AccessLevel.NONE)
    Map<String, SearchFilter> searchFilters = new HashMap<>();

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
     * Finds an Attribute by its column name.
     *
     * @param column the database column name of the attribute to find
     * @return an Optional containing the Attribute if found, or empty if not found
     */
    public Optional<Attribute> getAttributeByColumn(String column) {
        return Optional.ofNullable(columnAttributes.get(column));
    }


    /**
     * Finds an Attribute by its name.
     *
     * @param attributeName the name of the attribute to find
     * @return an Optional containing the Attribute if found, or empty if not found
     */
    public Optional<Attribute> getAttributeByName(String attributeName) {
        return Optional.ofNullable(attributes.get(attributeName));
    }

    /**
     * Finds a SearchFilter by its name.
     *
     * @param filterName the name of the filter to find
     * @return an Optional containing the SearchFilter if found, or empty if not found
     */
    public Optional<SearchFilter> getFilterByName(String filterName) {
        return Optional.ofNullable(searchFilters.get(filterName));
    }

}
