package com.contentgrid.appserver.json.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PatternConstaint implements AttributeConstraint {
    private String regex;
    private String htmlPattern;
}
