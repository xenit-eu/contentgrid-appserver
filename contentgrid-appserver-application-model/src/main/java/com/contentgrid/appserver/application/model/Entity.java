package com.contentgrid.appserver.application.model;

import java.util.List;
import java.util.Optional;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class Entity {

    @NonNull
    String name;

    @Singular
    List<Attribute> attributes;


    /**
     * Finds an Attribute by its name.
     *
     * @param attributeName the name of the attribute to find
     * @return an Optional containing the Attribute if found, or empty if not found
     */
    public Optional<Attribute> getAttributeByName(String attributeName) {
        return attributes.stream()
                .filter(attribute -> attribute.getName().equalsIgnoreCase(attributeName))
                .findFirst();
    }

}
