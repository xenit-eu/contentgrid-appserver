package com.contentgrid.appserver.application.model.searchfilters;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.exceptions.InvalidSearchFilterException;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
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
     * The path to the attribute this search filter operates on.
     * For simple attributes, this will be a single-element list.
     * For composite attributes, this will be a multi-element list representing the path.
     */
    @NonNull
    private final PropertyPath attributePath;

    /**
     * Constructs an AttributeSearchFilter with the specified parameters.
     *
     * @param name the name of the search filter
     * @param attributePath the path to the attribute to apply the filter on
     * @throws InvalidSearchFilterException if the attribute type is not supported
     */
    protected AttributeSearchFilter(
            @NonNull FilterName name,
            @NonNull PropertyPath attributePath
    ) {
        this.name = name;
        this.attributePath = attributePath;
    }

    /**
     * Determines if this search filter supports the given attribute.
     * 
     * @param attribute the attribute to check support for
     * @return true if the attribute is supported, false otherwise
     */
    public abstract boolean supports(SimpleAttribute attribute);

}
