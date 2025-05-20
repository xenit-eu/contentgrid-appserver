package com.contentgrid.appserver.json.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ManyToManyRelation extends Relation {
    private String joinTable;
    private String sourceReference;
    private String targetReference;
}
