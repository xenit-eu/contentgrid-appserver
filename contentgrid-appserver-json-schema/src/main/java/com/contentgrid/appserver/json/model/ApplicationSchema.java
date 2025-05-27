package com.contentgrid.appserver.json.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"$schema", "applicationName", "version", "entities", "relations"})
public class ApplicationSchema {
    @NonNull
    private String applicationName;
    private final String version = "1.0.0";
    private List<Entity> entities;
    private List<Relation> relations;

    @JsonProperty("$schema")
    private final String schema = "https://contentgrid.com/schemas/application-schema.json";

}
