package com.contentgrid.appserver.application.model.searchfilters;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.exceptions.InvalidSearchFilterException;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import java.util.List;
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
    PropertyPath attributePath;

    /**
     * The type of the target attribute.
     */
    @NonNull
    Type attributeType;

    /**
     * Constructs an AttributeSearchFilter with the specified parameters.
     *
     * @param name the name of the search filter
     * @param attributePath the path to the attribute to apply the filter on
     * @param attributeType the type of the target attribute
     * @throws InvalidSearchFilterException if the attribute type is not supported
     */
    protected AttributeSearchFilter(@NonNull FilterName name, @NonNull PropertyPath attributePath, @NonNull Type attributeType) {
        if (!supports(attributeType)) {
            throw new InvalidSearchFilterException("Attribute with type %s is not supported".formatted(attributeType));
        }
        if (attributePath.isEmpty()) {
            throw new InvalidSearchFilterException("Can't make an attribute search filter with an empty path. Path must lead to an attribute.");
        }
        this.name = name;
        this.attributePath = attributePath;
        this.attributeType = attributeType;
    }

    /**
     * Determines if this search filter supports the given attribute type.
     * 
     * @param type the attribute type to check support for
     * @return true if the attribute type is supported, false otherwise
     */
    protected abstract boolean supports(Type type);

}
