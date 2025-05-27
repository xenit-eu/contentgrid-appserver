package com.contentgrid.appserver.json.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Entity {

    @NonNull
    private String name;
    private String description;

    @NonNull
    private String table;

    @NonNull
    private String pathSegment;

    @NonNull
    private SimpleAttribute primaryKey;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<Attribute> attributes;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<SearchFilter> searchFilters;
}
