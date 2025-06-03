package com.contentgrid.appserver.application.model.searchfilters;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.exceptions.InvalidSearchFilterException;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import java.util.Set;
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
     * @param attributePath the path to the attribute to apply the filter on
     * @param attributeType the type of the target attribute (must be a native type)
     * @throws InvalidSearchFilterException if the attribute type is not supported
     */
    @Builder
    ExactSearchFilter(@NonNull FilterName name, @NonNull PropertyPath attributePath, @NonNull Type attributeType) throws InvalidSearchFilterException {
        super(name, attributePath, attributeType);
    }

    @Override
    protected boolean supports(Type type) {
        return Set.of(Type.values()).contains(type);
    }

    public static class ExactSearchFilterBuilder {
        public ExactSearchFilterBuilder attribute(@NonNull SimpleAttribute attribute) {
            this.attributePath = PropertyPath.of(attribute.getName());
            this.attributeType = attribute.getType();
            return this;
        }
    }
}
