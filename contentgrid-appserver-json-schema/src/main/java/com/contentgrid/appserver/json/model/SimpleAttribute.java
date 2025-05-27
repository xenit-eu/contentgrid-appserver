package com.contentgrid.appserver.json.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"type", "name", "description", "dataType", "columnName", "flags", "constraints"})
public final class SimpleAttribute extends Attribute {

    @NonNull
    private String columnName;

    @NonNull
    private String dataType;

    @JsonInclude(Include.NON_EMPTY)
    private List<AttributeConstraint> constraints;
}
