package com.contentgrid.appserver.application.model.openapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class OpenApiRequestBody {
    @JsonInclude(Include.NON_NULL)
    String description;
    @JsonInclude(Include.NON_DEFAULT)
    boolean required;
    @JsonInclude(Include.NON_NULL)
    final OpenApiMediaTypes content = new OpenApiMediaTypes();

}
