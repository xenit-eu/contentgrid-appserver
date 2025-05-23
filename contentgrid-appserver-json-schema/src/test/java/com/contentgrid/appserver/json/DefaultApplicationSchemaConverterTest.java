package com.contentgrid.appserver.json;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

import com.contentgrid.appserver.application.model.Application;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

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
            assertNotNull(is);
            var jsonSource = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            DefaultApplicationSchemaConverter converter = new DefaultApplicationSchemaConverter();
            Application app = converter.convert(new ByteArrayInputStream(jsonSource.getBytes(StandardCharsets.UTF_8)));
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            converter.toJson(app, out);
            String jsonTarget = out.toString(java.nio.charset.StandardCharsets.UTF_8);
            assertNotNull(jsonTarget);
            assertTrue(jsonTarget.contains("HR application")); // basic check for content
            assertTrue(jsonTarget.contains("entities"));
            assertTrue(jsonTarget.contains("relations"));

            assertThat(jsonTarget, sameJSONAs(jsonSource).allowingAnyArrayOrdering());
        }
    }
}
