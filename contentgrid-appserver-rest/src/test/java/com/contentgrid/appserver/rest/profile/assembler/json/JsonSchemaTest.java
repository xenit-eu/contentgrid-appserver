package com.contentgrid.appserver.rest.profile.assembler.json;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.rest.profile.assembler.json.JsonSchema.JsonSchemaProperty;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonSchemaTest {

    @Test
    void asStringArray() {
        var property = new JsonSchemaProperty("tags", null, null, false).asStringArray(null);

        assertThat(property.getType()).isEqualTo(JsonSchemaType.ARRAY);
        assertThat(property.getUniqueItems()).isTrue();
        assertThat(property.getItems()).isEqualTo(Map.of("type", "string"));
    }

    @Test
    void asStringArrayWithAllowedValues() {
        var property = new JsonSchemaProperty("labels", null, null, false)
                .asStringArray(List.of("hr", "it"));

        assertThat(property.getType()).isEqualTo(JsonSchemaType.ARRAY);
        assertThat(property.getUniqueItems()).isTrue();
        // The allowed values restrict the elements, not the array itself
        assertThat(property.getItems()).isEqualTo(Map.of("type", "string", "enum", List.of("hr", "it")));
    }
}
