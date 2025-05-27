package com.contentgrid.appserver.application.model.searchfilters;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.exceptions.InvalidSearchFilterException;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.FilterName;
import java.util.List;
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
     * @param attributePath the path to the attribute to apply the filter on
     * @param attributeType the type of the target attribute (must be of TEXT type)
     * @throws InvalidSearchFilterException if the attribute type is not supported
     */
    @Builder
    PrefixSearchFilter(@NonNull FilterName name, @NonNull List<AttributeName> attributePath, @NonNull Type attributeType) throws InvalidSearchFilterException {
        super(name, attributePath, attributeType);
    }

    @Override
    protected boolean supports(Type type) {
        return Type.TEXT.equals(type);
    }

    public static class PrefixSearchFilterBuilder {
        public PrefixSearchFilterBuilder attribute(@NonNull SimpleAttribute attribute) {
            this.attributePath = List.of(attribute.getName());
            this.attributeType = attribute.getType();
            return this;
        }
    }
}
