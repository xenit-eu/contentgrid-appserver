package com.contentgrid.appserver.application.model;

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

@Value
public class Entity {

    @Builder
    Entity(@NonNull String name, @NonNull String table, @Singular List<Attribute> attributes) {
        this.name = name;
        this.table = table;

        attributes.forEach(
            attribute -> {
                if (this.attributes.put(attribute.getName(), attribute) != null) {
                    throw new IllegalArgumentException("Duplicate attribute named %s".formatted(attribute.getName()));
                }
                if (this.columnAttributes.put(attribute.getColumn(), attribute) != null) {
                    throw new IllegalArgumentException("Duplicate column named %s".formatted(attribute.getColumn()));
                }
            }
        );
    }

    @NonNull
    String name;

    @NonNull
    String table;

    @Getter(AccessLevel.NONE)
    Map<String, Attribute> attributes = new HashMap<>();

    @Getter(AccessLevel.NONE)
    Map<String, Attribute> columnAttributes = new HashMap<>();

    /**
     * Returns an unmodifiable list of attributes.
     * @return an unmodifiable list of attributes
     */
    public List<Attribute> getAttributes() {
        return List.copyOf(attributes.values());
    }

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

}
