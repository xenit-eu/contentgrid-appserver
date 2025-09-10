package com.contentgrid.appserver.json.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.List;
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
    private String linkName;

    @JsonInclude(Include.NON_EMPTY)
    private List<String> flags;
}
