package com.contentgrid.appserver.application.model.openapi.model;

import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

@Data
@Accessors(chain = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OpenApiResponse {
    String summary;
    String description;
    final OpenApiHttpHeaders headers = new OpenApiHttpHeaders();
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
