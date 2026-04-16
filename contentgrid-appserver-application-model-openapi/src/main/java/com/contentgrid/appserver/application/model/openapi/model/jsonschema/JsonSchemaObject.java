package com.contentgrid.appserver.application.model.openapi.model.jsonschema;


import com.contentgrid.appserver.application.model.openapi.model.OpenApiPotentialReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class JsonSchemaObject extends AbstractJsonSchemaDataType {
    Map<String, OpenApiPotentialReference<JsonSchema>> properties = new LinkedHashMap<>();
    List<String> required = new ArrayList<>();

    public JsonSchemaObject() {
        super("object");
    }

    public JsonSchemaObject property(String name, OpenApiPotentialReference<JsonSchema> schema) {
        properties.put(name, schema);
        return this;
    }

    public JsonSchemaObject requiredProperty(String name, OpenApiPotentialReference<JsonSchema> schema) {
        property(name, schema);
        required.add(name);
        return this;
    }
}
