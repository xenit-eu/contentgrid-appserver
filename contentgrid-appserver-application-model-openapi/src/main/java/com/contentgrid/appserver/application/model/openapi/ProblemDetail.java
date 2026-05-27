package com.contentgrid.appserver.application.model.openapi;

import static com.contentgrid.appserver.application.model.openapi.ProblemDetail.ProblemDetailCustomizer.property;
import static com.contentgrid.appserver.application.model.openapi.ProblemDetail.ProblemDetailCustomizer.requiredProperty;

import com.contentgrid.appserver.application.model.openapi.model.OpenApiDiscriminator;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiPotentialReference;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiReference;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchema;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaAllOf;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaComposite;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaConst;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaDiscriminator;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaInteger;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaObject;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaString;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode
public class ProblemDetail {

    @NonNull
    @EqualsAndHashCode.Exclude
    private final OpenApiSpecContext context;

    @NonNull
    private final String subTypePrefix;

    @NonNull
    @Getter
    private final OpenApiReference<JsonSchema> schema;

    @Getter
    private final ProblemDetail base;

    public static ProblemDetail base(@NonNull OpenApiSpecContext context) {
        return base(context, "problemDetail",
                property("type", new JsonSchemaString()),
                property("title", new JsonSchemaString()),
                property("status", new JsonSchemaInteger()),
                property("detail", new JsonSchemaString()),
                property("instance", new JsonSchemaString())
        );
    }

    public static ProblemDetail base(@NonNull OpenApiSpecContext context, @NonNull String baseName,
            @NonNull ProblemDetailCustomizer... customizers) {
        var schema = context.spec().getComponents().getSchemas().register(baseName, self -> {
            var obj = new JsonSchemaObject();
            ProblemDetailCustomizer.compose(customizers).accept(self, obj);
            return new JsonSchemaComposite(
                    obj,
                    // empty discriminator, which might be filled in later when a subtype of this type calls 'registerDiscriminator()'
                    new JsonSchemaDiscriminator()
            );
        });
        return new ProblemDetail(
                context,
                baseName,
                schema,
                null
        );
    }

    private void registerDiscriminator(@NonNull String type, @NonNull OpenApiReference<JsonSchema> reference) {
        schema.getOriginalObject().ofType(JsonSchemaDiscriminator.class).ifPresent(discriminator -> {
            if(discriminator.getDiscriminator() == null) {
                discriminator.setDiscriminator(new OpenApiDiscriminator("type")
                        // Use itself as the fall-back mapping when there is no other one defined.
                        // The object itself will have all the base properties that any subtype must also contain
                        // so this will give some limited amount of information when deserializing an unknown type
                        .setDefaultMapping(schema)
                );
            }
            discriminator.getDiscriminator().mapping(type, reference);
        });

        if(base != null) {
            // Also register the type with the grandparent type
            base.registerDiscriminator(type, reference);
        }
    }

    public ProblemDetail subType(
            @NonNull String subName,
            ProblemDetailCustomizer... customizers
    ) {
        var subSchema = context.spec().getComponents().getSchemas()
                .register(this.subTypePrefix + "." + subName, self -> {
                    var object = new JsonSchemaObject();
                    ProblemDetailCustomizer.compose(customizers).accept(self, object);
                    return new JsonSchemaComposite(
                            new JsonSchemaAllOf(
                                    schema,
                                    object
                            ),
                            // empty discriminator, which might be filled in later when a subtype of this type calls 'registerDiscriminator()'
                            new JsonSchemaDiscriminator()
                    );
                });

        for (var customizer : customizers) {
            if (customizer instanceof ProblemDetailTypeSupplier typeSupplier) {
                for (var type : typeSupplier.types) {
                    registerDiscriminator(type, subSchema);
                }
            }
        }

        return new ProblemDetail(
                context,
                this.subTypePrefix + "." + subName,
                subSchema,
                this
        );
    }

    public ProblemDetail andSubType(
            @NonNull String subName,
            ProblemDetailCustomizer... customizers
    ) {
        subType(subName, customizers);
        return this;
    }

    /**
     * Customizes a Problem Details {@link JsonSchemaObject}
     */
    @FunctionalInterface
    public interface ProblemDetailCustomizer {

        static ProblemDetailCustomizer compose(
                ProblemDetailCustomizer... customizers) {
            return (self, object) -> {
                for (var customizer : customizers) {
                    customizer.accept(self, object);
                }
            };
        }

        static ProblemDetailCustomizer type(@NonNull String... types) {
            return new ProblemDetailTypeSupplier(types);
        }

        static ProblemDetailCustomizer title(@NonNull String title) {
            return (self, object) -> object.setTitle(title);
        }

        static ProblemDetailCustomizer description(@NonNull String description) {
            return (self, object) -> object.setDescription(description);
        }

        static ProblemDetailCustomizer status(int statusCode) {
            return requiredProperty("status", new JsonSchemaComposite(
                    new JsonSchemaInteger(),
                    new JsonSchemaConst(statusCode)
            ));
        }

        static ProblemDetailCustomizer requiredProperty(@NonNull String name, @NonNull JsonSchema schema) {
            return (self, object) -> object.requiredProperty(name, schema);
        }

        static ProblemDetailCustomizer requiredProperty(@NonNull String name,
                Function<OpenApiPotentialReference<JsonSchema>, JsonSchema> factory) {
            return (self, object) -> object.requiredProperty(name, factory.apply(self));

        }

        static ProblemDetailCustomizer property(@NonNull String name, @NonNull JsonSchema property) {
            return (self, object) -> object.property(name, property);
        }

        void accept(OpenApiPotentialReference<JsonSchema> self, JsonSchemaObject object);

    }

    @RequiredArgsConstructor
    private static class ProblemDetailTypeSupplier implements ProblemDetailCustomizer {

        private final String[] types;

        @Override
        public void accept(OpenApiPotentialReference<JsonSchema> self, JsonSchemaObject object) {
            // Empty, because it doesn't actually place an enum on the object.
            // This class is handled specially to configure the discriminator
            // Placing an enum on an inherited problem detail breaks openapi code generators
        }
    }

}
