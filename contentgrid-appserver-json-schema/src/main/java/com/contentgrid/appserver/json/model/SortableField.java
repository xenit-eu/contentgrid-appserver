package com.contentgrid.appserver.json.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Getter
@Setter
public class SortableField {

    @NonNull
    private String name;

    @NonNull
    private List<PropertyPathElement> attributePath;
}