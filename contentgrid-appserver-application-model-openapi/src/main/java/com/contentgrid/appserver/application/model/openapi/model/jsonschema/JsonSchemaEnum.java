package com.contentgrid.appserver.application.model.openapi.model.jsonschema;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public class JsonSchemaEnum extends JsonSchemaString {

    @Getter(AccessLevel.NONE)
    List<String> enum_;

    @JsonProperty("enum")
    public List<String> getEnum() {
        return this.enum_;
    }
}
