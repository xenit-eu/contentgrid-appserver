package com.contentgrid.appserver.json.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RelationEndPoint {
    private String name;
    private String pathSegment;

    @NonNull
    private String entityName;
    private String description;
    private boolean required;
    private String linkName;
}
