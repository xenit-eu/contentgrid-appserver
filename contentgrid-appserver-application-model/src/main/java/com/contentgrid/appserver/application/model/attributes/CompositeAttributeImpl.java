package com.contentgrid.appserver.application.model.attributes;

import com.contentgrid.appserver.application.model.attributes.flags.AttributeFlag;
import com.contentgrid.appserver.application.model.exceptions.DuplicateElementException;
import com.contentgrid.appserver.application.model.exceptions.InvalidAttributeTypeException;
import com.contentgrid.appserver.application.model.values.AttributeName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

@Value
public class CompositeAttributeImpl implements CompositeAttribute {

    @NonNull
    AttributeName name;

    String description;

    Set<AttributeFlag> flags;

    @Getter(AccessLevel.NONE)
    Map<AttributeName, Attribute> attributes = new HashMap<>();

    @Builder
    CompositeAttributeImpl(@NonNull AttributeName name, String description, @Singular Set<Attribute> attributes, @Singular Set<AttributeFlag> flags) {
        this.name = name;
        this.description = description;
        this.flags = flags;
        for (var attribute : attributes) {
            if(attribute instanceof ContentAttribute) {
                throw new InvalidAttributeTypeException("Composite attribute '%s' can not contain content attributes".formatted(name));
            }
            if (this.attributes.put(attribute.getName(), attribute) != null) {
                throw new DuplicateElementException("Duplicate attribute named %s".formatted(attribute.getName()));
            }
        }
        for (var flag : this.flags) {
            flag.checkSupported(this);
        }
    }

    /**
     * Returns an unmodifiable list of attributes.
     *
     * @return an unmodifiable list of attributes
     */
    @Override
    public List<Attribute> getAttributes() {
        return List.copyOf(attributes.values());
    }

    /**
     * Finds an Attribute by its name.
     *
     * @param attributeName the name of the attribute to find
     * @return an Optional containing the Attribute if found, or empty if not found
     */
    @Override
    public Optional<Attribute> getAttributeByName(AttributeName attributeName) {
        return Optional.ofNullable(attributes.get(attributeName));
    }
}
