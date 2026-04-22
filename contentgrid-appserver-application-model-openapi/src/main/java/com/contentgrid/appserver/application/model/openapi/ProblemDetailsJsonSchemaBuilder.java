package com.contentgrid.appserver.application.model.openapi;

import com.contentgrid.appserver.application.model.openapi.model.OpenApiPotentialReference;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchema;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaAllOf;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaConst;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaEnum;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaInteger;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaObject;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaString;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaString.Format;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ProblemDetailsJsonSchemaBuilder {

    @NonNull
    private final OpenApiSpecContext context;

    public OpenApiPotentialReference<JsonSchema> createGeneric() {
        return context.spec().getComponents().getSchemas().register("problemDetail", () -> new JsonSchemaObject()
                .property("type", new JsonSchemaString())
                .property("title", new JsonSchemaString())
                .property("status", new JsonSchemaInteger())
                .property("detail", new JsonSchemaString())
                .property("instance", new JsonSchemaString())
        );
    }

    public OpenApiPotentialReference<JsonSchema> createGeneric(
            @NonNull String schemaName,
            @NonNull ProblemDetailsCustomizer... customizers
    ) {
        return createGeneric(schemaName, null, customizers);
    }

    public OpenApiPotentialReference<JsonSchema> createGeneric(
            @NonNull String schemaName,
            OpenApiPotentialReference<JsonSchema> base,
            @NonNull ProblemDetailsCustomizer... customizers
    ) {
        return context.spec().getComponents().getSchemas().register(schemaName + "ProblemDetail", self -> {
            var object = new JsonSchemaObject();
            ProblemDetailsCustomizer.compose(customizers).accept(self, object);
            return new JsonSchemaAllOf(
                    Objects.requireNonNullElseGet(base, this::createGeneric),
                    object
            );
        });
    }


    @FunctionalInterface
    public interface ProblemDetailsCustomizer {

        static ProblemDetailsCustomizer compose(ProblemDetailsCustomizer... customizers) {
            return (self, object) -> {
                for (var customizer : customizers) {
                    customizer.accept(self, object);
                }
            };
        }

        static ProblemDetailsCustomizer type(@NonNull JsonSchema problemDetailType) {
            return requiredProperty("type", problemDetailType);
        }

        static ProblemDetailsCustomizer type(@NonNull String... types) {
            return switch (types.length) {
                case 0 -> type(new JsonSchemaString().setFormat(Format.URI));
                case 1 -> type(new JsonSchemaConst(types[0]));
                default -> type(new JsonSchemaEnum(List.of(types)));
            };
        }

        static ProblemDetailsCustomizer status(int statusCode) {
            return requiredProperty("status", new JsonSchemaConst(statusCode));
        }

        static ProblemDetailsCustomizer requiredProperty(@NonNull String name, @NonNull JsonSchema schema) {
            return (self, object) -> object.requiredProperty(name, schema);
        }

        static ProblemDetailsCustomizer requiredProperty(@NonNull String name,
                Function<OpenApiPotentialReference<JsonSchema>, JsonSchema> factory) {
            return (self, object) -> object.requiredProperty(name, factory.apply(self));

        }

        static ProblemDetailsCustomizer property(@NonNull String name, @NonNull JsonSchema property) {
            return (self, object) -> object.property(name, property);
        }

        void accept(OpenApiPotentialReference<JsonSchema> self, JsonSchemaObject object);
    }

}
