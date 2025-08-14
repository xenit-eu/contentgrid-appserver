package com.contentgrid.appserver.application.model.searchfilters;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.exceptions.InvalidSearchFilterException;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;

/**
 * A search filter that performs full text search on the given attribute for the given value. It supports text-type
 * attributes
 */
@EqualsAndHashCode(callSuper = true)
@Value
public class FullTextSearchFilter extends AttributeSearchFilter {

    /**
     * Constructa a FullTextSearchFilter with specified parameters.
     *
     * @param name the name of the search filter
     * @param attributePath the path to the attribute to apply the filter on
     * @param attributeType the type of the target attribute (must be of TEXT type)
     * @throws InvalidSearchFilterException if the attribute type is not supported
     */
    @Builder
    FullTextSearchFilter(@NonNull FilterName name, @NonNull PropertyPath attributePath, @NonNull Type attributeType)
            throws InvalidSearchFilterException {
        super(name, attributePath, attributeType);
    }

    @Override
    protected boolean supports(Type type) {
        return Type.TEXT.equals(type);
    }

    public static class FullTextSearchFilterBuilder {

        public FullTextSearchFilterBuilder attribute(@NonNull SimpleAttribute attribute) {
            this.attributePath = PropertyPath.of(attribute.getName());
            this.attributeType = attribute.getType();
            return this;
        }
    }
}
