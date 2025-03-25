package com.contentgrid.appserver.application.model.searchfilters;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.exceptions.InvalidSearchFilterException;
import com.contentgrid.appserver.application.model.values.FilterName;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;

/**
 * A search filter that performs exact matching on attribute values.
 * 
 * This filter can be used to find entities where an attribute exactly matches a specified value.
 * It supports all native attribute types.
 */
@EqualsAndHashCode(callSuper = true)
@Value
public class ExactSearchFilter extends AttributeSearchFilter {

    /**
     * Constructs an ExactSearchFilter with the specified parameters.
     *
     * @param name the name of the search filter
     * @param attribute the attribute to apply the filter on (must be a native type)
     * @throws InvalidSearchFilterException if the attribute type is not supported
     */
    @Builder
    ExactSearchFilter(FilterName name, @NonNull SimpleAttribute attribute) throws InvalidSearchFilterException {
        super(name, attribute);
    }

    @Override
    protected boolean supports(Type type) {
        return Type.NATIVE_TYPES.contains(type);
    }

    @Override
    protected FilterName defaultFilterName(SimpleAttribute attribute) {
        return attribute.getName().toFilterName();
    }
}
