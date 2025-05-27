package com.contentgrid.appserver.json.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ManyToManyRelation extends Relation {

    @NonNull
    private String joinTable;

    @NonNull
    private String sourceReference;

    @NonNull
    private String targetReference;
}
