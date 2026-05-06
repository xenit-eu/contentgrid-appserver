package com.contentgrid.appserver.application.model.openapi.model.rest.body;

import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.APPLICATION;
import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.EMPTY_WITH_ETAG;
import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.INVOICE;
import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.ORDER;
import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.PERSON;
import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.PERSON_FRIENDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.assertj.core.condition.AllOf.allOf;

import com.contentgrid.appserver.application.model.Constraint.AllowedValuesConstraint;
import com.contentgrid.appserver.application.model.Constraint.RegexPatternConstraint;
import com.contentgrid.appserver.application.model.Constraint.UniqueConstraint;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures;
import com.contentgrid.appserver.application.model.i18n.UserLocales;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.BodyObjectMapper.Context;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BodyObjectMapperTest {

    private static final UserLocales ENGLISH = new UserLocales() {
        @Override
        public Locale resolvePreferredLocale(java.util.Collection<Locale> supportedLocales) {
            return supportedLocales.stream().filter(l -> l.getLanguage().equals("en")).findFirst().orElse(null);
        }
        @Override
        public java.util.stream.Stream<Locale> preferredLocales() {
            return java.util.stream.Stream.of(Locale.ENGLISH);
        }
    };

    @Nested
    class ForBody {

        private static BodyObjectMapper.Context context(SupportedCombination combination) {
            return new Context(APPLICATION, combination.bodyType(), combination.mediaType(), ENGLISH);
        }

        @RequiredArgsConstructor
        @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
        @Getter
        @Accessors(fluent = true)
        enum SupportedCombination {
            POST_JSON(BodyType.POST, MediaType.JSON),
            POST_JSON_FLAT(BodyType.POST, MediaType.FLAT_JSON),
            POST_FORM(BodyType.POST, MediaType.FORM),
            POST_MULTIPART(BodyType.POST, MediaType.MULTIPART_FORM),
            PUT_JSON(BodyType.PUT, MediaType.JSON),
            PUT_JSON_FLAT(BodyType.PUT, MediaType.FLAT_JSON),
            PUT_FORM(BodyType.PUT, MediaType.FORM),
            PATCH_JSON(BodyType.PUT, MediaType.JSON),
            PATCH_JSON_FLAT(BodyType.PUT, MediaType.FLAT_JSON),
            PATCH_FORM(BodyType.PUT, MediaType.FORM),
            RESPONSE_JSON(BodyType.RESPONSE, MediaType.JSON),
            ;
            BodyType bodyType;
            MediaType mediaType;

            public Condition<BodyValue> jsonNullable() {
                if (mediaType.canTransportNulls()) {
                    return new Condition<>(BodyValue::isNullable, "nullable for the %s mediatype", mediaType);
                } else {
                    // forms can't have null values, those can only be expressed by the field being absent
                    // so a form BodyValue can't be nullable; but one that can be null in JSON should also never be mandatory
                    return allOf(
                            new Condition<BodyValue>(v -> !v.isNullable(), "not nullable for the %s mediatype",
                                    mediaType),
                            new Condition<BodyValue>(v -> !v.isMandatory(), "not mandatory for the %s mediatype",
                                    mediaType)
                    ).as("not nullable for the %s mediatype (but also not mandatory)", mediaType);
                }
            }

            public Condition<BodyValue> normallyMandatory() {
                if (bodyType != BodyType.PATCH) {
                    return new Condition<>(BodyValue::isMandatory, "mandatory in the %s body", bodyType);
                }
                return new Condition<>(v -> !v.isMandatory(), "not mandatory in the %s body", bodyType);
            }

            public Condition<BodyValue> responseMandatory() {
                if (bodyType == BodyType.RESPONSE) {
                    return new Condition<>(BodyValue::isMandatory, "only mandatory in the %s body", bodyType);
                } else {
                    return new Condition<>(v -> !v.isMandatory(), "not mandatory in the %s body", bodyType);
                }
            }
        }

        @ParameterizedTest
        @EnumSource(SupportedCombination.class)
        void primaryKey(SupportedCombination combination) {
            var result = BodyObjectMapper.forBody(context(combination), INVOICE.getName());

            var fieldAssert = assertThat(result.getField("invoice_id"));
            if (combination.bodyType() == BodyType.RESPONSE) {
                fieldAssert.hasValueSatisfying(value -> {
                    assertThat(value).isInstanceOfSatisfying(SimpleBodyValue.class, simpleBodyValue -> {
                        assertThat(simpleBodyValue.isMandatory()).isTrue();
                        assertThat(simpleBodyValue.isNullable()).isFalse();
                        assertThat(simpleBodyValue.getType()).isEqualTo(Type.UUID);
                    });
                });
            } else {
                fieldAssert.isEmpty();
            }
        }

        @ParameterizedTest
        @EnumSource(SupportedCombination.class)
        void readonlyAttribute(SupportedCombination combination) {
            var result = BodyObjectMapper.forBody(context(combination), INVOICE.getName());

            var fieldAssert = assertThat(result.getField("audit_metadata"));
            if (combination.bodyType() == BodyType.RESPONSE) {
                fieldAssert.isPresent();
            } else {
                fieldAssert.isEmpty();
            }
        }

        @ParameterizedTest
        @EnumSource(SupportedCombination.class)
        void requiredAttribute(SupportedCombination combination) {
            var result = BodyObjectMapper.forBody(context(combination), INVOICE.getName());

            assertThat(result.getField("number")).hasValueSatisfying(value -> {
                assertThat(value).is(combination.normallyMandatory());
                assertThat(value.isNullable()).isFalse();
            });
        }


        @ParameterizedTest
        @EnumSource(SupportedCombination.class)
        void nullableAttribute(SupportedCombination combination) {
            var result = BodyObjectMapper.forBody(context(combination), INVOICE.getName());

            assertThat(result.getField("received")).hasValueSatisfying(value -> {
                assertThat(value).is(combination.responseMandatory());
                assertThat(value).is(combination.jsonNullable());
            });
        }


        @ParameterizedTest
        @EnumSource(SupportedCombination.class)
        void contentAttribute(SupportedCombination combination) {
            var result = BodyObjectMapper.forBody(context(combination), INVOICE.getName());

            Function<String, Optional<BodyValue>> getSubField =
                    combination.mediaType() == MediaType.JSON ? f -> (result.getField("content")
                            .map(ObjectBodyValue.class::cast)).flatMap(c -> c.getField(f))
                            : f -> result.getField("content." + f);

            var fieldAssert = assertThat(result.getField("content"));
            var maybeFilename = getSubField.apply("filename");
            var maybeMimetype = getSubField.apply("mimetype");
            var maybeLength = getSubField.apply("length");
            var subFieldsAssert = assertThat(List.of(maybeFilename, maybeMimetype, maybeLength));

            if (combination.bodyType() == BodyType.POST) {
                if (combination.mediaType() == MediaType.MULTIPART_FORM) {
                    fieldAssert.hasValueSatisfying(value -> {
                        assertThat(value.isMandatory()).isFalse();
                        assertThat(value.isNullable()).isFalse();
                        assertThat(value).isInstanceOf(ContentBodyValue.class);
                    });
                } else {
                    fieldAssert.isEmpty();
                }
                subFieldsAssert.allSatisfy(f -> assertThat(f).isEmpty());
            } else {
                if (combination.mediaType() != MediaType.JSON) {
                    fieldAssert.isEmpty();
                }
                assertThat(maybeFilename).hasValueSatisfying(value -> {
                    assertThat(value).isInstanceOfSatisfying(SimpleBodyValue.class, simpleBodyValue -> {
                        assertThat(simpleBodyValue).is(combination.responseMandatory());
                        assertThat(simpleBodyValue).is(combination.jsonNullable());
                        assertThat(simpleBodyValue.getType()).isEqualTo(Type.TEXT);
                    });
                });
                assertThat(maybeMimetype).hasValueSatisfying(value -> {
                    assertThat(value).isInstanceOfSatisfying(SimpleBodyValue.class, simpleBodyValue -> {
                        if (combination.mediaType() == MediaType.JSON) {
                            // This is in a nested object, it can be marked mandatory because it's required when the object exists
                            assertThat(simpleBodyValue).is(combination.normallyMandatory());
                        } else {
                            // This is a flattened object, it can't be marked mandatory, because there technically isn't an object around it
                            assertThat(simpleBodyValue.isMandatory()).isFalse();
                        }
                        assertThat(simpleBodyValue.isNullable()).isFalse();
                        assertThat(simpleBodyValue.getType()).isEqualTo(Type.TEXT);
                    });
                });
                if (combination.bodyType() == BodyType.RESPONSE) {
                    assertThat(maybeLength).hasValueSatisfying(value -> {
                        assertThat(value.isMandatory()).isTrue();
                        assertThat(value.isNullable()).isFalse();
                    });
                } else {
                    assertThat(maybeLength).isEmpty();
                }
            }
        }

        @ParameterizedTest
        @EnumSource(SupportedCombination.class)
        void userAttribute(SupportedCombination combination) {
            var result = BodyObjectMapper.forBody(context(combination), ORDER.getName());
            var fieldAssert = assertThat(result.getField("created_by"));

            if (combination == SupportedCombination.RESPONSE_JSON) {
                fieldAssert.hasValueSatisfying(value -> {
                    assertThat(value).isInstanceOfSatisfying(SimpleBodyValue.class, simpleBodyValue -> {
                        assertThat(simpleBodyValue.getType()).isEqualTo(Type.TEXT);
                    });
                });
            } else {
                fieldAssert.isEmpty();
            }
        }

        @ParameterizedTest
        @EnumSource(SupportedCombination.class)
        void hiddenAttribute(SupportedCombination combination) {
            var result = BodyObjectMapper.forBody(context(combination), EMPTY_WITH_ETAG.getName());

            if (combination.bodyType() == BodyType.RESPONSE) {
                assertThat(result.getFields())
                        .containsOnlyKeys("empty_with_etag_id");
            } else {
                assertThat(result.getFields()).isEmpty();
            }
        }

        @Test
        void attributeConstraintsPreserved() {
            var result = BodyObjectMapper.forBody(
                    new Context(ModelTestFixtures.APPLICATION, BodyType.RESPONSE, MediaType.JSON, ENGLISH),
                    INVOICE.getName());
            assertThat(result.getField("confidentiality")).get()
                    .isInstanceOfSatisfying(SimpleBodyValue.class, value -> {
                        assertThat(value.getConstraint(AllowedValuesConstraint.class)).isPresent();
                    });
            assertThat(result.getField("number")).get()
                    .isInstanceOfSatisfying(SimpleBodyValue.class, value -> {
                        // irrelevant constraints are not preserved
                        assertThat(value.getConstraint(UniqueConstraint.class)).isEmpty();
                        assertThat(value.getConstraint(RegexPatternConstraint.class)).isPresent();
                    });
        }

        @ParameterizedTest
        @EnumSource(SupportedCombination.class)
        void requiredRelation(SupportedCombination combination) {
            var result = BodyObjectMapper.forBody(context(combination), INVOICE.getName());

            var fieldAssert = assertThat(result.getField("customer"));

            // Relations are only present on initial creation
            if (combination.bodyType() == BodyType.POST) {
                fieldAssert.hasValueSatisfying(value -> {
                    assertThat(value.isMandatory()).isTrue();
                    assertThat(value.isNullable()).isFalse();

                    assertThat(value).isInstanceOfSatisfying(RelationBodyValue.class, relationBodyValue -> {
                        assertThat(relationBodyValue.getTargetEntity()).isEqualTo(PERSON.getName());
                    });
                });

            } else {
                fieldAssert.isEmpty();
            }
        }

        @ParameterizedTest
        @EnumSource(SupportedCombination.class)
        void optionalRelations(SupportedCombination combination) {
            var result = BodyObjectMapper.forBody(context(combination), INVOICE.getName());

            assertThat(List.of(
                    result.getField("previous_invoice"),
                    result.getField("next_invoice")
            )).allSatisfy(maybeField -> {
                // Relations are only present on initial creation
                if (combination.bodyType() == BodyType.POST) {
                    assertThat(maybeField).hasValueSatisfying(value -> {
                        assertThat(value.isMandatory()).isFalse();
                        assertThat(value).is(combination.jsonNullable());
                    });
                } else {
                    assertThat(maybeField).isEmpty();
                }
            });
        }

        @ParameterizedTest
        @EnumSource(SupportedCombination.class)
        void toManyRelation(SupportedCombination combination) {
            var result = BodyObjectMapper.forBody(context(combination), INVOICE.getName());

            var fieldAssert = assertThat(result.getField("products"));
            // Relations are only present on initial creation
            if (combination.bodyType() == BodyType.POST) {
                fieldAssert.hasValueSatisfying(value -> {
                    // The array can not be set to null (can be an empty array), but is is not required to be present either
                    assertThat(value.isNullable()).isFalse();
                    assertThat(value.isMandatory()).isFalse();
                    assertThat(value).isInstanceOfSatisfying(ArrayBodyValue.class, arrayBodyValue -> {
                        assertThat(arrayBodyValue.getItems()).isInstanceOfSatisfying(RelationBodyValue.class,
                                relationBodyValue -> {
                                    // Items in the array can't be null either
                                    assertThat(value.isNullable()).isFalse();
                                });
                    });
                });
            } else {
                fieldAssert.isEmpty();
            }
        }

        @ParameterizedTest
        @EnumSource(SupportedCombination.class)
        void hiddenRelation(SupportedCombination combination) {
            var result = BodyObjectMapper.forBody(context(combination), PERSON.getName());
            // A hidden relation is never exposed, also not in POST body
            assertThat(result.getField(PERSON_FRIENDS.getTargetEndPoint().getName().getValue())).isEmpty();
        }

        @Test
        void allResponseFieldsAreMandatory() {
            var result = BodyObjectMapper.forBody(context(SupportedCombination.RESPONSE_JSON), INVOICE.getName());
            assertThat(result.getFields().values())
                    .isNotEmpty()
                    .allMatch(BodyValue::isMandatory);
        }

        @ParameterizedTest
        @EnumSource(SupportedCombination.class)
        void noPatchFieldIsMandatory(SupportedCombination combination) {
            assumeThat(combination.bodyType()).isEqualTo(BodyType.PATCH);

            var result = BodyObjectMapper.forBody(context(combination), INVOICE.getName());
            assertThat(result.getFields().values())
                    .noneMatch(BodyValue::isMandatory);
        }

        @ParameterizedTest
        @EnumSource(SupportedCombination.class)
        void noFormFieldsAreNullable(SupportedCombination combination) {
            assumeThat(combination.mediaType()).isIn(MediaType.FORM, MediaType.MULTIPART_FORM);

            var result = BodyObjectMapper.forBody(context(combination), INVOICE.getName());
            assertThat(result.getFields().values())
                    .noneMatch(BodyValue::isNullable);
        }

        @ParameterizedTest
        @EnumSource(SupportedCombination.class)
        void translations(SupportedCombination combination) {
            var result = BodyObjectMapper.forBody(context(combination), PERSON.getName());

            assertThat(result.getTitle()).isEqualTo("Person");
            assertThat(result.getDescription()).isEqualTo("Humans known by the system");

            assertThat(result.getField("name")).hasValueSatisfying(value -> {
                assertThat(value.getTitle()).isEqualTo("Full name");
                assertThat(value.getDescription()).isEqualTo("Full legal name of the person");
            });

            if (combination.bodyType() == BodyType.POST) {
                assertThat(result.getField("parent")).hasValueSatisfying(value -> {
                    assertThat(value.getTitle()).isEqualTo("Parent");
                });
            }
        }

    }

    @Nested
    class ForSearch {

        @Test
        void allSearchFiltersAreMappedAsNonMandatoryAndNonNullable() {
            var result = BodyObjectMapper.forSearch(APPLICATION, ENGLISH, INVOICE.getName());
            assertThat(result.getFields()).isNotEmpty();
            assertThat(result.getFields().values())
                    .allMatch(v -> !v.isMandatory())
                    .allMatch(v -> !v.isNullable());
        }

        @Test
        void hiddenSearchFilterIsExcluded() {
            // "customer" has HiddenSearchFilterFlag
            var result = BodyObjectMapper.forSearch(APPLICATION, ENGLISH, INVOICE.getName());
            assertThat(result.getFields()).doesNotContainKey("customer");
        }

        @Test
        void visibleSearchFiltersAreIncluded() {
            var result = BodyObjectMapper.forSearch(APPLICATION, ENGLISH, INVOICE.getName());
            assertThat(result.getField("number")).hasValueSatisfying(value -> {
                assertThat(value).isInstanceOfSatisfying(SimpleBodyValue.class, simpleBodyValue -> {
                    assertThat(simpleBodyValue.getType()).isEqualTo(Type.TEXT);
                    assertThat(simpleBodyValue.getConstraints()).satisfiesExactlyInAnyOrder(constraint -> {
                        // Constraints are applied to search forms, only for exact matches
                        assertThat(constraint).isInstanceOf(RegexPatternConstraint.class);
                    });
                });
            });
            assertThat(result.getField("amount")).hasValueSatisfying(value -> {
                assertThat(value).isInstanceOfSatisfying(SimpleBodyValue.class, simpleBodyValue -> {
                    assertThat(simpleBodyValue.getType()).isEqualTo(Type.DOUBLE);
                });
            });
            assertThat(result.getField("customer.name~prefix")).hasValueSatisfying(value -> {
                assertThat(value).isInstanceOfSatisfying(SimpleBodyValue.class, simpleBodyValue -> {
                    assertThat(simpleBodyValue.getType()).isEqualTo(Type.TEXT);
                });
            });
            assertThat(result.getField("previous_invoice.confidentiality")).hasValueSatisfying(value -> {
                assertThat(value).isInstanceOfSatisfying(SimpleBodyValue.class, simpleBodyValue -> {
                    assertThat(simpleBodyValue.getType()).isEqualTo(Type.TEXT);
                    assertThat(simpleBodyValue.getConstraints()).satisfiesExactlyInAnyOrder(constraint -> {
                        // Constraints are applied to search forms, only for exact matches
                        assertThat(constraint).isInstanceOf(AllowedValuesConstraint.class);
                    });
                });
            });

        }

        @Test
        void translations() {
            var result = BodyObjectMapper.forSearch(APPLICATION, ENGLISH, PERSON.getName());
            assertThat(result.getField("name~prefix")).hasValueSatisfying(value -> {
                assertThat(value.getTitle()).isEqualTo("Name starts with");
            });

            assertThat(result.getField("friends.name~prefix")).hasValueSatisfying(value -> {
                assertThat(value.getTitle()).isEqualTo("Has a friend with name");
            });
        }
    }
}
