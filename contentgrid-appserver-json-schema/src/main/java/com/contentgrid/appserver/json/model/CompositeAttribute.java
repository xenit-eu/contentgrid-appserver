package com.contentgrid.appserver.json.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CompositeAttribute implements Attribute {
    private String name;
    private String description;
    private String type; // always "composite"
    private List<Attribute> attributes;
    private List<String> flags;
}
