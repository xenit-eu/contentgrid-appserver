package com.contentgrid.appserver.application.model.openapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

@Data
@Accessors(chain = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OpenApiResponse {
    @JsonInclude(Include.NON_EMPTY)
    String summary;
    @JsonInclude(Include.NON_EMPTY)
    String description;
    @JsonInclude(Include.NON_EMPTY)
    final OpenApiHttpHeaders headers = new OpenApiHttpHeaders();
    @JsonInclude(Include.NON_EMPTY)
    final OpenApiMediaTypes content = new OpenApiMediaTypes();

    public OpenApiResponse headers(Consumer<OpenApiHttpHeaders> headersConsumer) {
        headersConsumer.accept(headers);
        return this;
    }

    public OpenApiResponse content(Consumer<OpenApiMediaTypes> contentConsumer) {
        contentConsumer.accept(content);
        return this;
    }
}
