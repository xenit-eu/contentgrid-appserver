package com.contentgrid.appserver.application.model.values;

import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;

public sealed interface PropertyPath permits AttributePath, RelationPath {
    @NonNull PropertyName getFirst();
    PropertyPath getRest();

    static PropertyPath of(AttributeName attribute) {
        return new SimpleAttributePath(attribute);
    }

    static PropertyPath of(List<PropertyName> propertyNames) {
        PropertyName[] array = propertyNames.toArray(new PropertyName[0]);
        return of(array);
    }

    static PropertyPath of(PropertyName... propertyNames) {
        if (propertyNames.length == 0 || !(propertyNames[propertyNames.length - 1] instanceof AttributeName)) {
            throw new IllegalArgumentException("Property path must be not empty and end with an Attribute");
        }
        PropertyPath path = new SimpleAttributePath((AttributeName) propertyNames[propertyNames.length - 1]);
        for (int i = propertyNames.length - 2; i >= 0; i--) {
            if (propertyNames[i] instanceof AttributeName attr && path instanceof AttributePath rest) {
                path = new CompositeAttributePath(attr, rest);
            } else if (propertyNames[i] instanceof RelationName rel) {
                path = new RelationPath(rel, path);
            } else {
                throw new IllegalArgumentException("Invalid PropertyPath: Cannot nest relation in attribute");
            }
        }

        return path;
    }

    PropertyPath withSuffix(AttributeName attributeName);

    default PropertyPath withPrefix(PropertyName propertyName) {
        return switch (propertyName) {
            case AttributeName attributeName -> withPrefix(attributeName);
            case RelationName relationName -> withPrefix(relationName);
        };
    }

    AttributePath withPrefix(AttributeName attributeName);

    default RelationPath withPrefix(RelationName relationName) {
        return new RelationPath(relationName, this);
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

}
