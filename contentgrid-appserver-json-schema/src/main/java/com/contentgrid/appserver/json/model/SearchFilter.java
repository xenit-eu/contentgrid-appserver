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
public class SearchFilter {

    @NonNull
    private String name;

    @NonNull
    private List<PropertyPathElement> attributePath;

    @NonNull
    private String type; // exact, prefix

    @JsonInclude(Include.NON_EMPTY)
    private List<String> flags;
}
