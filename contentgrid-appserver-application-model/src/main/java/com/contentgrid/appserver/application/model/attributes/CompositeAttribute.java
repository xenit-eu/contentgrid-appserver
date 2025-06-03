package com.contentgrid.appserver.application.model.attributes;

import com.contentgrid.appserver.application.model.HasAttributes;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import java.util.List;
import java.util.Optional;

public sealed interface CompositeAttribute extends Attribute, HasAttributes permits CompositeAttributeImpl, ContentAttribute, UserAttribute {

    /**
     * Returns an unmodifiable list of attributes.
     *
     * @return an unmodifiable list of attributes
     */
    List<Attribute> getAttributes();

    /**
     * Finds an Attribute by its name.
     *
     * @param attributeName the name of the attribute to find
     * @return an Optional containing the Attribute if found, or empty if not found
     */
    default Optional<Attribute> getAttributeByName(AttributeName attributeName) {
        return getAttributes().stream()
                .filter(attribute -> attribute.getName().equals(attributeName))
                .findAny();
    }

    @Override
    default List<ColumnName> getColumns() {
        return getAttributes().stream()
                .flatMap(attribute -> attribute.getColumns().stream())
                .toList();
    }
}
