package com.contentgrid.appserver.json.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchFilter {

    @NonNull
    private String name;

    @NonNull
    private String attributeName;

    @NonNull
    private String type; // exact, prefix
}
