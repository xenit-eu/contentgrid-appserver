package com.contentgrid.appserver.application.model.searchfilters;

import com.contentgrid.appserver.application.model.Attribute;
import com.contentgrid.appserver.application.model.Attribute.Type;
import com.contentgrid.appserver.application.model.exceptions.InvalidSearchFilterException;
import lombok.Getter;
import lombok.NonNull;

/**
 * Base class for search filters that operate on entity attributes.
 * 
 * AttributeSearchFilter is an abstract class that provides common functionality
 * for search filters that filter entities based on attribute values.
 */
@Getter
public abstract class AttributeSearchFilter implements SearchFilter {

    /**
     * The name of the search filter.
     */
    @NonNull
    String name;

    /**
     * The attribute this search filter operates on.
     */
    @NonNull
    Attribute attribute;

    protected AttributeSearchFilter(@NonNull String name, @NonNull Attribute attribute) {
        if (!supports(attribute.getType())) {
            throw new InvalidSearchFilterException("Attribute with type %s is not supported".formatted(attribute.getType()));
        }
        this.name = name;
        this.attribute = attribute;
    }

    /**
     * Determines if this search filter supports the given attribute type.
     * 
     * @param type the attribute type to check support for
     * @return true if the attribute type is supported, false otherwise
     */
    protected abstract boolean supports(Type type);

}
