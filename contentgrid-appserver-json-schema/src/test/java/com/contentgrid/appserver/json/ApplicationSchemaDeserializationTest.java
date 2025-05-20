package com.contentgrid.appserver.json;

import com.contentgrid.appserver.json.model.ApplicationSchema;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationSchemaDeserializationTest {
    @Test
    void testDeserializeSampleApplicationJson() throws Exception {
        ObjectMapper mapper = ApplicationSchemaObjectMapperFactory.createObjectMapper();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("sample-application.json")) {
            assertNotNull(is, "sample-application.json should be on the classpath");
            ApplicationSchema schema = mapper.readValue(is, ApplicationSchema.class);
            assertNotNull(schema);
            assertEquals("HR application", schema.getApplicationName());
            assertEquals("1.0.0", schema.getVersion());
            assertNotNull(schema.getEntities());
            assertFalse(schema.getEntities().isEmpty());
            assertNotNull(schema.getRelations());
            assertFalse(schema.getRelations().isEmpty());
        }
    }
}
