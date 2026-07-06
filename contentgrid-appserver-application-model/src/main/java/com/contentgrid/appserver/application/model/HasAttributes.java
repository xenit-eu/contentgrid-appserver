package com.contentgrid.appserver.application.model;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.propertypath.AttributePath;
import com.contentgrid.appserver.application.model.propertypath.CompositeAttributePath;
import com.contentgrid.appserver.application.model.propertypath.SimpleAttributePath;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.Value;

/**
 * Interface for objects that contain attributes and can look them up by name.
 */
public interface HasAttributes {
    Optional<Attribute> getAttributeByName(AttributeName attributeName);
    List<Attribute> getAttributes();

    /**
     * Locates a potentially nested attribute by its path
     * @param path The path to look up the attribute with
     * @return The attribute referenced by the path
     */
    default Optional<Attribute> getNestedAttribute(@NonNull AttributePath path) {
        var maybeAttribute = getAttributeByName(path.getFirst());
        return switch (path) {
            case SimpleAttributePath simpleAttributePath -> maybeAttribute;
            case CompositeAttributePath compositeAttributePath -> maybeAttribute.flatMap(attr -> {
                if (attr instanceof HasAttributes hasAttributes) {
                    return hasAttributes.getNestedAttribute(path.getRest());
                }
                return Optional.empty();
            });
        };
    }

    /**
     * Streams all nested attributes.
     * <p>
     * Composite attributes themselves are also returned, before their nested attributes
     * @return A stream of all nested attributes
     */
    default Stream<Entry<Attribute>> nestedAttributes() {
        return getAttributes().stream()
                .flatMap(attribute -> {
                    var self = Stream.of(new Entry<>(new SimpleAttributePath(attribute.getName()), attribute));
                    if(attribute instanceof HasAttributes hasAttributes) {
                        return Stream.concat(
                                self,
                                hasAttributes.nestedAttributes()
                                        .map(nestedAttribute -> nestedAttribute.withPrefix(attribute.getName()))
                        );
                    }
                    return self;
                });
    }

    /**
     * Streams all nested attributes of a certain type.
     * <p>
     * Composite attributes are also available and are always expanded before filtering by <code>attributeType</code>
     * @param attributeType The class of the attribute to filter by
     * @return A stream of all nested attributes of a certain type
     * @param <T> The type of the attribute
     */
    default <T extends Attribute> Stream<Entry<T>> nestedAttributes(@NonNull Class<T> attributeType) {
        return nestedAttributes()
                .flatMap(entry -> entry.forType(attributeType).stream());
    }

    @Value
    class Entry<T extends Attribute> {

        @NonNull
        AttributePath path;

        @NonNull
        T attribute;

        private Entry<T> withPrefix(@NonNull AttributeName attributeName) {
            return new Entry<>(new CompositeAttributePath(attributeName, path), attribute);
        }

        private <U extends Attribute> Optional<Entry<U>> forType(Class<U> attributeType) {
            if(attributeType.isInstance(attribute)) {
                return Optional.of((Entry<U>)this);
            }
            return Optional.empty();
        }
    }
}