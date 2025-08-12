package com.contentgrid.appserver.application.model.searchfilters;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.exceptions.InvalidSearchFilterException;
import com.contentgrid.appserver.application.model.searchfilters.flags.SearchFilterFlag;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import java.util.Set;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Singular;
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
     * @throws InvalidSearchFilterException if the attribute type is not supported
     */
    @Builder
    PrefixSearchFilter(
            @NonNull FilterName name,
            @NonNull PropertyPath attributePath,
            @NonNull @Singular Set<SearchFilterFlag> flags
    ) throws InvalidSearchFilterException {
        super(name, attributePath, flags);
    }

    @Override
    public boolean supports(SimpleAttribute attribute) {
        return attribute.getType().equals(Type.TEXT);
    }

    public static class PrefixSearchFilterBuilder {
        public PrefixSearchFilterBuilder attribute(@NonNull SimpleAttribute attribute) {
            this.attributePath = PropertyPath.of(attribute.getName());
            return this;
        }
    }
}
