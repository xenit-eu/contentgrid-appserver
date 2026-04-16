package com.contentgrid.appserver.application.model.openapi.model.jsonschema;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
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
public sealed class JsonSchemaString extends AbstractJsonSchemaDataType permits JsonSchemaEnum {

    @JsonInclude(Include.NON_NULL)
    String pattern;

    @JsonInclude(Include.NON_NULL)
    Format format;

    public JsonSchemaString() {
        super("string");
    }

    public enum Format {
        @JsonProperty("date")
        DATE,
        @JsonProperty("date-time")
        DATE_TIME,
        @JsonProperty("binary")
        BINARY,
        @JsonProperty("uri")
        URI
    }
}
