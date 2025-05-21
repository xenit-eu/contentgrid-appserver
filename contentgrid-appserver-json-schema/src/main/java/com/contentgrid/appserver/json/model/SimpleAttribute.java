package com.contentgrid.appserver.json.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SimpleAttribute extends Attribute {
    private String columnName;
    private String dataType;
    private List<AttributeConstraint> constraints;
}
