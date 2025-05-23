package com.contentgrid.appserver.json.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"type", "name", "description", "idColumn", "namespaceColumn", "userNameColumn", "flags"})
public final class UserAttribute extends Attribute {
    private String idColumn;
    private String namespaceColumn;
    private String userNameColumn;
}
