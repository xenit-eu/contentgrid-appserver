package com.contentgrid.appserver.application.model.openapi;

import com.contentgrid.appserver.application.model.openapi.model.OpenApiDiscriminator;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiPotentialReference;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiReference;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.AbstractJsonSchemaDataType;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchema;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaAllOf;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaConst;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaEnum;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaInteger;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaObject;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaOneOf;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaString;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaString.Format;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Factory for problem details JSON schemas,
 * allowing for easy sub-typing and composition of different problem details.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class ProblemDetailsJsonSchema implements OpenApiPotentialReference<JsonSchema> {

    @NonNull
    private final OpenApiSpecContext context;

    @NonNull
    private final String subTypePrefix;

    @NonNull
    @JsonValue
    @Getter
    private final OpenApiPotentialReference<JsonSchema> schema;

    private final ProblemDetailsJsonSchema baseType;

    private final List<ProblemDetailsJsonSchema> subTypes = new ArrayList<>();

    public static ProblemDetailsJsonSchema base(@NonNull OpenApiSpecContext context) {
        var schema = context.spec().getComponents().getSchemas().register("problemDetail", () -> new JsonSchemaObject()
                .property("type", new JsonSchemaString())
                .property("title", new JsonSchemaString())
                .property("status", new JsonSchemaInteger())
                .property("detail", new JsonSchemaString())
                .property("instance", new JsonSchemaString())
        );
        return new ProblemDetailsJsonSchema(
                context,
                "problemDetail",
                schema,
                null
        );
    }

    @Override
    public JsonSchema getOriginalObject() {
        return schema.getOriginalObject();
    }

    public Optional<ProblemDetailsJsonSchema> baseType() {
        return Optional.ofNullable(baseType);
    }

    public Stream<ProblemDetailsJsonSchema> subTypes() {
        return subTypes.stream();
    }

    public ProblemDetailsJsonSchema baseType(
            @NonNull String subName,
            ProblemDetailsCustomizer... customizers
    ) {
        return genericSubType(
                subName + ".base",
                subName,
                customizers
        );
    }

    private ProblemDetailsJsonSchema genericSubType(
            @NonNull
            String subName,
            @NonNull
            String subTypePrefix,
            ProblemDetailsCustomizer... customizers
    ) {
        var subSchema = context.spec().getComponents().getSchemas()
                .register(this.subTypePrefix + "." + subName, self -> {
                    var object = new JsonSchemaObject();
                    ProblemDetailsCustomizer.compose(customizers).accept(self, object);
                    return new JsonSchemaAllOf(
                            schema,
                            object
                    );
                });

        var subType = new ProblemDetailsJsonSchema(
                context,
                this.subTypePrefix + "." + subTypePrefix,
                subSchema,
                this
        );

        subTypes.add(subType);

        return subType;
    }

    public ProblemDetailsJsonSchema subType(
            @NonNull String subName,
            ProblemDetailsCustomizer... customizers
    ) {
        return genericSubType(
                subName,
                subName,
                customizers
        );
    }

    public ProblemDetailsJsonSchema andSubType(
            @NonNull String subName,
            ProblemDetailsCustomizer... customizers
    ) {
        subType(subName, customizers);
        return this;
    }

    public OpenApiPotentialReference<JsonSchema> composite() {
        return composite(oneOf -> {
            var discriminator = new OpenApiDiscriminator("type");

            for (var subType : subTypes) {
                var problemDetailTypes = getProblemDetailType(subType.getOriginalObject())
                        .flatMap(s -> s instanceof JsonSchemaConst schemaConst?Stream.of(schemaConst.getConst()):Stream.empty())
                        .filter(Objects::nonNull)
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .toList();

                for (var problemDetailType : problemDetailTypes) {
                    discriminator.mapping(problemDetailType, (OpenApiReference<JsonSchema>) subType.getSchema());

                }
            }
            discriminator.setDefaultMapping((OpenApiReference<JsonSchema>) schema);

            oneOf.setDiscriminator(discriminator);
        });
    }

    private static Stream<JsonSchema> getProblemDetailType(JsonSchema schema) {
        return switch (schema) {
            case JsonSchemaOneOf oneOf -> oneOf.getOneOf().stream().flatMap(s -> getProblemDetailType(s.getOriginalObject())).filter(Objects::nonNull);
            case JsonSchemaAllOf allOf -> allOf.getAllOf().stream().flatMap(s -> getProblemDetailType(s.getOriginalObject())).filter(Objects::nonNull);
            case JsonSchemaObject object -> Optional.ofNullable(object.getProperties().get("type")).map(OpenApiPotentialReference::getOriginalObject).stream();
            case AbstractJsonSchemaDataType abstractJsonSchemaDataType -> Stream.empty();
            case JsonSchemaConst jsonSchemaConst -> Stream.empty();
        };
    }

    public OpenApiPotentialReference<JsonSchema> composite(Consumer<JsonSchemaOneOf> compositeModifier) {
        return context.spec().getComponents().getSchemas().register(subTypePrefix+".composite", self -> {
           var schema = new JsonSchemaOneOf(this.subTypes);
           compositeModifier.accept(schema);
           return schema;
        });
    }

    /**
     * Customizes a Problem Details {@link JsonSchemaObject}
     */
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

        static ProblemDetailsCustomizer title(@NonNull String title) {
            return (self, object) -> object.setTitle(title);
        }

        static ProblemDetailsCustomizer description(@NonNull String description) {
            return (self, object) -> object.setDescription(description);
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
