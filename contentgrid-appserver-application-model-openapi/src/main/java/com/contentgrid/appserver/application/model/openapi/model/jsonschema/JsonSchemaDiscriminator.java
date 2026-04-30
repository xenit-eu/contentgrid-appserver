package com.contentgrid.appserver.application.model.openapi.model.jsonschema;

import com.contentgrid.appserver.application.model.openapi.model.OpenApiDiscriminator;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Accessors(chain = true)
public final class JsonSchemaDiscriminator implements JsonSchema {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    OpenApiDiscriminator discriminator;

    public JsonSchemaDiscriminator(@NonNull String propertyName) {
        this(new OpenApiDiscriminator(propertyName));
    }

}
