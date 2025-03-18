package com.contentgrid.appserver.application.model.searchfilters;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@Value
@SuperBuilder
public class PrefixSearchFilter extends AttributeSearchFilter {

}
