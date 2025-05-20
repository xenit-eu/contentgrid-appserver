package com.contentgrid.appserver.json.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Entity {
    private String name;
    private String description;
    private String table;
    private String pathSegment;
    private SimpleAttribute primaryKey;
    private List<Attribute> attributes;
    private List<SearchFilter> searchFilters;
}
