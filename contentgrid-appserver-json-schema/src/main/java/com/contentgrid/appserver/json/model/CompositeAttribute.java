package com.contentgrid.appserver.json.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"type", "name", "description", "flags", "attributes"})
public final class CompositeAttribute extends Attribute {
    private List<Attribute> attributes;
}
