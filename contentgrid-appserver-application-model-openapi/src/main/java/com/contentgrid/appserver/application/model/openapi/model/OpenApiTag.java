package com.contentgrid.appserver.application.model.openapi.model;

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
    String summary;
    String description;
    String kind;
}
