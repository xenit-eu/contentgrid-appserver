package com.contentgrid.appserver.application.model.openapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Data
@Accessors(chain = true)
public class OpenApiTag {
    @NonNull
    String name;
    @JsonInclude(Include.NON_NULL)
    String summary;
    @JsonInclude(Include.NON_NULL)
    String description;
    @JsonInclude(Include.NON_NULL)
    String kind;
}
