package com.contentgrid.appserver.application.model.searchfilters;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.values.FilterName;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;

/**
 * A search filter that performs prefix matching on text attribute values.
 * 
 * This filter can be used to find entities where a text attribute starts with a specified prefix.
 * It only supports attributes of TEXT type.
 */
@EqualsAndHashCode(callSuper = true)
@Value
public class PrefixSearchFilter extends AttributeSearchFilter {

    /**
     * Constructs a PrefixSearchFilter with the specified parameters.
     *
     * @param name the name of the search filter
     * @param attribute the attribute to apply the filter on (must be of TEXT type)
     * @throws InvalidSearchFilterException if the attribute type is not supported
     */
    @Builder
    PrefixSearchFilter(FilterName name, @NonNull SimpleAttribute attribute) {
        super(name, attribute);
    }

    @Override
    protected boolean supports(Type type) {
        return Type.TEXT.equals(type);
    }

    @Override
    protected FilterName defaultFilterName(SimpleAttribute attribute) {
        return attribute.getName().toFilterName().withSuffix("~prefix");
    }
}
