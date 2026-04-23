package com.contentgrid.appserver.application.model.openapi.model.jsonschema;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Data
@Accessors(chain = true)
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@JsonPropertyOrder({"type", "format"})
public sealed class JsonSchemaString extends AbstractJsonSchemaDataType permits JsonSchemaEnum {

    @JsonInclude(Include.NON_NULL)
    String pattern;

    @JsonInclude(Include.NON_NULL)
    Format format;

    public JsonSchemaString() {
        super("string");
    }

    public enum Format {
        DATE,
        DATE_TIME,
        BINARY,
        URI,
        DECIMAL,
        UUID;

        @JsonValue
        String jsonValue() {
            return name().replace('_', '-').toLowerCase(Locale.ROOT);
        }
    }
}
