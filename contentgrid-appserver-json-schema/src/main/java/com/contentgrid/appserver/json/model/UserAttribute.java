package com.contentgrid.appserver.json.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"type", "name", "description", "idColumn", "namespaceColumn", "userNameColumn", "flags"})
public final class UserAttribute extends Attribute {

    @NonNull
    private String idColumn;

    @NonNull
    private String namespaceColumn;

    @NonNull
    private String userNameColumn;
}
