package com.contentgrid.appserver.json.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.List;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(Include.NON_NULL)
public class SortableField {

    @NonNull
    private String name;

    @NonNull
    private List<PropertyPathElement> attributePath;
}