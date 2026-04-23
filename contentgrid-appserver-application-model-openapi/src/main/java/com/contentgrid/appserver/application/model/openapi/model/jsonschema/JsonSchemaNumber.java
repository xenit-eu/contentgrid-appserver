package com.contentgrid.appserver.application.model.openapi.model.jsonschema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import lombok.experimental.Accessors;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@JsonPropertyOrder({"type", "format"})
@Accessors(chain = true)
@Data
public sealed class JsonSchemaNumber extends AbstractJsonSchemaDataType permits JsonSchemaInteger {

    @JsonInclude(Include.NON_NULL)
    Format format;
    JsonSchemaNumber(@NonNull String type) {
        super(type);
    }

    public JsonSchemaNumber() {
        super("number");
    }

    public enum Format {
        INT32,
        INT64,
        UINT64,
        FLOAT,
        DOUBLE,
        DECIMAL
        ;

        @JsonValue
        String jsonValue() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
