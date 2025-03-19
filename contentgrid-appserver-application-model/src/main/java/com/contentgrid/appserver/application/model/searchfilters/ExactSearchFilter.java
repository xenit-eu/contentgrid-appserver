package com.contentgrid.appserver.application.model.searchfilters;

import com.contentgrid.appserver.application.model.Attribute;
import com.contentgrid.appserver.application.model.Attribute.Type;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public class ExactSearchFilter extends AttributeSearchFilter {

    @Builder
    ExactSearchFilter(@NonNull String name, @NonNull Attribute attribute) {
        super(name, attribute);
    }

    @Override
    protected boolean supports(Type type) {
        return Type.NATIVE_TYPES.contains(type);
    }
}
