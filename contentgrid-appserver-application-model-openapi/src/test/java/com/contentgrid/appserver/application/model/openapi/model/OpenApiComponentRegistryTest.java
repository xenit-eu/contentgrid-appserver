package com.contentgrid.appserver.application.model.openapi.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.application.model.openapi.model.OpenApiComponents.OpenApiComponentRegistry;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaObject;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaString;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OpenApiComponentRegistryTest {

    @Test
    void registersNewComponent() {
        var schemas = new OpenApiComponents().getSchemas();

        var ref = schemas.register("test", () -> new JsonSchemaString());

        assertThat(ref).isEqualTo(new OpenApiReference<>("#/components/schemas/test", null));
        assertThat(schemas.getItems()).containsEntry("test", new JsonSchemaString());
    }

    @Test
    void register_withInstance() {
        var schemas = new OpenApiComponents().getSchemas();

        var ref = schemas.register("test", new JsonSchemaString());

        assertThat(ref).isEqualTo(new OpenApiReference<>("#/components/schemas/test", null));
        assertThat(schemas.getItems()).containsEntry("test", new JsonSchemaString());
    }

    @Test
    void doesNotOverwriteExistingComponent() {
        var schemas = new OpenApiComponents().getSchemas();
        schemas.register("test", () -> new JsonSchemaString());

        schemas.register("test", () -> new JsonSchemaObject());

        assertThat(schemas.getItems()).containsEntry("test", new JsonSchemaString());
    }

    @Test
    void supplierNotCalledIfAlreadyRegistered() {
        var schemas = new OpenApiComponents().getSchemas();
        schemas.register("test", () -> new JsonSchemaString());

        var callCount = new AtomicInteger(0);
        schemas.register("test", () -> {
            callCount.incrementAndGet();
            return new JsonSchemaObject();
        });

        assertThat(callCount).hasValue(0);
    }

    @Test
    void referenceHasCorrectPrefix() {
        var pathItems = new OpenApiComponents().getPathItems();

        var ref = pathItems.register("content", () -> new OpenApiPaths.OpenApiPathItem());

        assertThat(ref).isEqualTo(new OpenApiReference<>("#/components/pathItems/content", null));
    }
}
