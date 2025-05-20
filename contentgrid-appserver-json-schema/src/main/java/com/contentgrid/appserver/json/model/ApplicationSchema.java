package com.contentgrid.appserver.json.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApplicationSchema {
    private String applicationName;
    private String version;
    private List<Entity> entities;
    private List<Relation> relations;
}
