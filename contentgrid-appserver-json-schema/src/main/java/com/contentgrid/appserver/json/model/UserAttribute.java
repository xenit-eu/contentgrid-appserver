package com.contentgrid.appserver.json.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserAttribute implements Attribute {
    private String name;
    private String description;
    private String type; // always "user"
    private List<String> flags;
    private String idColumn;
    private String namespaceColumn;
    private String userNameColumn;
}
