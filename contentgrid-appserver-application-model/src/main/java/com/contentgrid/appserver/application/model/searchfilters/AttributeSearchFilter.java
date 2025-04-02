package com.contentgrid.appserver.application.model.searchfilters;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.exceptions.InvalidSearchFilterException;
import com.contentgrid.appserver.application.model.values.FilterName;
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
    FilterName name;

    /**
     * The attribute this search filter operates on.
     */
    @NonNull
    SimpleAttribute attribute;

    /**
     * Constructs an AttributeSearchFilter with the specified parameters.
     *
     * @param name the name of the search filter
     * @param attribute the attribute to apply the filter on
     * @throws InvalidSearchFilterException if the attribute type is not supported
     */
    protected AttributeSearchFilter(@NonNull FilterName name, @NonNull SimpleAttribute attribute) {
        if (!supports(attribute.getType())) {
            throw new InvalidSearchFilterException("SimpleAttribute with type %s is not supported".formatted(attribute.getType()));
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
