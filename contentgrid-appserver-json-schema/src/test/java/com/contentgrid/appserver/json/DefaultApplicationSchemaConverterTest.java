package com.contentgrid.appserver.json;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.json.model.ApplicationSchema;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class DefaultApplicationSchemaConverterTest {
    @Test
    void testConvertSampleApplicationJson() throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("sample-application.json")) {
            Application app = new DefaultApplicationSchemaConverter().convert(is);
            assertNotNull(app);
            assertEquals("HR application", app.getName().getValue());
            assertFalse(app.getEntities().isEmpty());
            assertFalse(app.getRelations().isEmpty());
            // Optionally, add more assertions for entities, attributes, and relations
        }
    }


    @Test
    void testToJsonWritesOutput() throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("sample-application.json")) {
            DefaultApplicationSchemaConverter converter = new DefaultApplicationSchemaConverter();
            Application app = converter.convert(is);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            converter.toJson(app, out);
            String json = out.toString(java.nio.charset.StandardCharsets.UTF_8);
            assertNotNull(json);
            assertTrue(json.contains("HR application")); // basic check for content
            assertTrue(json.contains("entities"));
            assertTrue(json.contains("relations"));
        }
    }
}
