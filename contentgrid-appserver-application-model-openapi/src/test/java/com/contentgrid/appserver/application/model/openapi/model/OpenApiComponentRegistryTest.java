package com.contentgrid.appserver.application.model.openapi.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.application.model.openapi.model.OpenApiPaths.OpenApiPathItem;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaObject;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaString;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OpenApiComponentRegistryTest {

    @Test
    void registersNewComponent() {
        var schemas = new OpenApiComponents().getSchemas();

        var ref = schemas.register("test", JsonSchemaString::new);

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
        schemas.register("test", JsonSchemaString::new);

        schemas.register("test", JsonSchemaObject::new);

        assertThat(schemas.getItems()).containsEntry("test", new JsonSchemaString());
    }

    @Test
    void supplierNotCalledIfAlreadyRegistered() {
        var schemas = new OpenApiComponents().getSchemas();
        schemas.register("test", JsonSchemaString::new);

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

        var ref = pathItems.register("content", OpenApiPathItem::new);

        assertThat(ref).isEqualTo(new OpenApiReference<>("#/components/pathItems/content", null));
    }
}
