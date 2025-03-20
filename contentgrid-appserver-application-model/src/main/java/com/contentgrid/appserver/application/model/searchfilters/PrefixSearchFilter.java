package com.contentgrid.appserver.application.model.searchfilters;

import com.contentgrid.appserver.application.model.Attribute;
import com.contentgrid.appserver.application.model.Attribute.Type;
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

    @Builder
    PrefixSearchFilter(@NonNull String name, @NonNull Attribute attribute) {
        super(name, attribute);
    }

    @Override
    protected boolean supports(Type type) {
        return Type.TEXT.equals(type);
    }
}
