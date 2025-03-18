package com.contentgrid.appserver.application.model.searchfilters;


import com.contentgrid.appserver.application.model.Attribute;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public abstract class AttributeSearchFilter implements SearchFilter {

    @NonNull
    String name;

    @NonNull
    Attribute attribute;

}
