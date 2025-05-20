package com.contentgrid.appserver.json.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ManyToManyRelation implements Relation {
    private String type; // always "many-to-many"
    private RelationEndPoint sourceEndpoint;
    private RelationEndPoint targetEndpoint;
    private String joinTable;
    private String sourceReference;
    private String targetReference;
}
