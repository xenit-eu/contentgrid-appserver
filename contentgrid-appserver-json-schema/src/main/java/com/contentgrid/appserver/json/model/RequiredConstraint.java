package com.contentgrid.appserver.json.model;

import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonInclude;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RequiredConstraint implements AttributeConstraint {
    private String type; // always "required"
}
