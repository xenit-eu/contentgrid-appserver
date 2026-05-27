package com.contentgrid.appserver.application.model.openapi.model.jsonschema;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
@ToString(callSuper = true)
@JsonPropertyOrder({"type", "format", "title", "description", "enum", "examples"})
public class JsonSchemaEnum extends JsonSchemaString {

    @Getter(AccessLevel.NONE)
    List<String> enum_;

    @JsonProperty("enum")
    public List<String> getEnum() {
        return this.enum_;
    }

    @Override
    public List<Object> getExamples() {
        return enum_.stream()
                .filter(Objects::nonNull)
                .findFirst()
                .<List<Object>>map(List::of)
                .orElseGet(List::of);
    }
}
