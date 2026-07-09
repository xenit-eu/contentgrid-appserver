package com.contentgrid.appserver.application.model.propertypath;

import com.contentgrid.appserver.application.model.propertypath.CompositeRelationPath.CompositeRelationPathToAttribute;
import com.contentgrid.appserver.application.model.propertypath.CompositeRelationPath.CompositeRelationPathToRelation;
import com.contentgrid.appserver.application.model.propertypath.PropertyPath.CrossesAttribute;
import com.contentgrid.appserver.application.model.propertypath.PropertyPath.CrossesRelation;
import com.contentgrid.appserver.application.model.propertypath.PropertyPath.ResolvesToAttribute;
import com.contentgrid.appserver.application.model.propertypath.PropertyPath.ResolvesToRelation;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.PropertyName;
import com.contentgrid.appserver.application.model.values.RelationName;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;
import lombok.SneakyThrows;

/**
 * A path to any <i>property</i> (an attribute or relation) on an entity (or across entities when following relations).
 * <p>
 * A path resolves to either an attribute or a relation.
 * <p>
 * There are 2 different viewpoints possible on a property path:
 *  <ol>
 *      <li>Where the next step goes to (an attribute {@link CrossesAttribute} or a relation {@link CrossesRelation}). This is useful when resolving the path</li>
 *      <li>Where the resolved path terminates (an attribute {@link ResolvesToAttribute} or a relation {@link ResolvesToRelation}). This is useful when using the typechecker to ensure that a correct target is selected.</li>
 *  </ol>
 *
 * When using types, you usually only need the types described above.
 */
public sealed interface PropertyPath extends Serializable permits CrossesAttribute, CrossesRelation,
        ResolvesToAttribute, ResolvesToRelation {
    @NonNull
    PropertyName getFirst();
    PropertyPath getRest();

    /**
     * Creates a path to an attribute
     * @param attributeNames The list of attribute names to traverse to arrive at a specific attribute
     * @return The path to the attribute
     */
    @SneakyThrows(InvalidPropertyPathException.class)
    static AttributePath toAttribute(AttributeName... attributeNames) {
        return of(attributeNames).as(AttributePath.class);
    }

    /**
     * Creates a path to an attribute
     * @param propertyNames The list of property names to traverse to arrive at a specific attribute. The last item must be an {@link AttributeName}
     * @return The path to the attribute
     * @throws InvalidPropertyPathException If the path does not resolve to an attribute
     */
    static ResolvesToAttribute toAttribute(PropertyName... propertyNames) throws InvalidPropertyPathException {
        return of(propertyNames).as(ResolvesToAttribute.class);
    }

    /**
     * Creates a path to an attribute
     * @param propertyNames The list of property names to traverse to arrive at a specific attribute. The last item must be an {@link AttributeName}
     * @return the path to the attribute
     */
    @SneakyThrows(InvalidPropertyPathException.class)
    static ResolvesToAttribute toAttributeUnchecked(PropertyName... propertyNames) {
        return toAttribute(propertyNames);
    }


    /**
     * Creates a path to a property
     * @param propertyNames The list of property names to traverse to arrive at an attribute or relation
     * @return the path to the property
     */
    static PropertyPath of(List<PropertyName> propertyNames) {
        return of(propertyNames.toArray(new PropertyName[0]));
    }

    /**
     * Creates a path to a property
     * @param propertyNames The list of property names to traverse to arrive at an attribute or relation
     * @return the path to the property
     */
    static PropertyPath of(PropertyName... propertyNames) {
        if (propertyNames.length == 0) {
            throw new IllegalArgumentException("Property path must not be empty");
        }
        var position = propertyNames.length - 1;
        PropertyPath path = switch (propertyNames[position]) {
            case AttributeName attributeName -> new SimpleAttributePath(attributeName);
            case RelationName relationName -> new SimpleRelationPath(relationName);
        };
        position--;
        for(; position >= 0; position--) {
            path = switch (propertyNames[position]) {
                case AttributeName attributeName -> {
                    try {
                        yield new CompositeAttributePath(attributeName, path.as(AttributePath.class));
                    } catch (InvalidPropertyPathException e) {
                        throw new IllegalArgumentException("Invalid PropertyPath: Cannot nest relation in attribute", e);
                    }
                }
                case RelationName relationName -> CompositeRelationPath.of(relationName, path);
            };
        }

        return path;
    }

    /**
     * Asserts that the property path is a specific subtype
     * @param type The required subtype
     * @return This property path, cast to the required subtype
     * @param <T> The subtype
     * @throws InvalidPropertyPathException If the property path is not of the required subtype
     */
    default <T extends PropertyPath> T as(Class<T> type) throws InvalidPropertyPathException {
        if (!type.isInstance(this)) {
            throw new InvalidPropertyPathException(this, type);
        }
        return (T)this;
    }

    default List<String> toList() {
        PropertyPath path = this;
        List<String> list = new ArrayList<>();
        do {
            list.add(path.getFirst().getValue());
            path = path.getRest();
        } while (path != null);
        return list;
    }

    /**
     * Marks a property path that finally resolves to an attribute
     */
    sealed interface ResolvesToAttribute extends PropertyPath permits AttributePath, CompositeRelationPathToAttribute {
        @Override
        ResolvesToAttribute getRest();
    }

    /**
     * Marks a property path that finally resolves to a relation
     */
    sealed interface ResolvesToRelation extends PropertyPath permits CompositeRelationPathToRelation,
            SimpleRelationPath {
        @Override
        ResolvesToRelation getRest();
    }

    /**
     * Marks a property path whose first component is an attribute
     */
    sealed interface CrossesAttribute extends PropertyPath permits AttributePath {
        @Override
        AttributeName getFirst();
    }

    /**
     * Marks a property path whose first component is a relation
     */
    sealed interface CrossesRelation extends PropertyPath permits CompositeRelationPath,
            SimpleRelationPath {
        @Override
        RelationName getFirst();
    }
}
