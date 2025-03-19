package com.contentgrid.appserver.application.model.searchfilters;

import com.contentgrid.appserver.application.model.Attribute;
import com.contentgrid.appserver.application.model.Attribute.Type;
import lombok.Getter;
import lombok.NonNull;

@Getter
public abstract class AttributeSearchFilter implements SearchFilter {

    @NonNull
    String name;

    @NonNull
    Attribute attribute;

    protected AttributeSearchFilter(@NonNull String name, @NonNull Attribute attribute) {
        if (!supports(attribute.getType())) {
            throw new IllegalArgumentException("Attribute with type %s is not supported".formatted(attribute.getType()));
        }
        this.name = name;
        this.attribute = attribute;
    }

    protected abstract boolean supports(Type type);

}
