package com.contentgrid.appserver.application.model.openapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

/**
 * Adds metadata to a single tag that is used by the Operation Object.
 * @see <a href="https://spec.openapis.org/oas/v3.2.0.html#tag-object">Tag Object</a>
 */
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
