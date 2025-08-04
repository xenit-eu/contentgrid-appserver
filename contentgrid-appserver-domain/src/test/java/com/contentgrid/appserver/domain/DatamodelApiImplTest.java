package com.contentgrid.appserver.domain;

import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.APPLICATION;
import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.INVOICE;
import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.INVOICE_AMOUNT;
import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.INVOICE_AUDIT_METADATA;
import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.INVOICE_CONTENT;
import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.INVOICE_CUSTOMER;
import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.INVOICE_IS_PAID;
import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.INVOICE_NUMBER;
import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.INVOICE_PAY_BEFORE;
import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.INVOICE_RECEIVED;
import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.PERSON;
import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.PERSON_AGE;
import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.PERSON_NAME;
import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.PERSON_VAT;
import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.PRODUCT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.domain.data.DataEntry.FileDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MissingDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.NullDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.RelationDataEntry;
import com.contentgrid.appserver.domain.data.InvalidDataTypeException;
import com.contentgrid.appserver.domain.data.MapRequestInputData;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.domain.data.validation.RequiredConstraintViolationInvalidDataException;
import com.contentgrid.appserver.query.engine.api.QueryEngine;
import com.contentgrid.appserver.query.engine.api.UpdateResult;
import com.contentgrid.appserver.query.engine.api.data.CompositeAttributeData;
import com.contentgrid.appserver.query.engine.api.data.EntityCreateData;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.query.engine.api.data.SimpleAttributeData;
import com.contentgrid.appserver.query.engine.api.data.XToManyRelationData;
import com.contentgrid.appserver.query.engine.api.data.XToOneRelationData;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DatamodelApiImplTest {
    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    private QueryEngine queryEngine;

    private DatamodelApi datamodelApi;

    private final Clock clock = Clock.fixed(Instant.ofEpochSecond(440991035), ZoneOffset.UTC);

    private static CompositeAttributeData getAuditMetadataData() {
        // TODO: have these attributes be filled automatically
        return CompositeAttributeData.builder()
                .name(INVOICE_AUDIT_METADATA.getName())
                .attribute(new SimpleAttributeData<>(AttributeName.of("created_date"), null))
                .attribute(CompositeAttributeData.builder()
                        .name(AttributeName.of("created_by"))
                        .attribute(new SimpleAttributeData<>(AttributeName.of("id"), null))
                        .attribute(new SimpleAttributeData<>(AttributeName.of("namespace"), null))
                        .attribute(new SimpleAttributeData<>(AttributeName.of("name"), null))
                        .build())
                .attribute(new SimpleAttributeData<>(AttributeName.of("last_modified_date"), null))
                .attribute(CompositeAttributeData.builder()
                        .name(AttributeName.of("last_modified_by"))
                        .attribute(new SimpleAttributeData<>(AttributeName.of("id"), null))
                        .attribute(new SimpleAttributeData<>(AttributeName.of("namespace"), null))
                        .attribute(new SimpleAttributeData<>(AttributeName.of("name"), null))
                        .build())
                .build();
    }

    @BeforeEach
    void setup() {
        datamodelApi = new DatamodelApiImpl(queryEngine);
    }

    @Nested
    class CreateEntity {
        @Test
        void allSimpleProperties_succeeds() throws InvalidPropertyDataException {
            var createDataCaptor = ArgumentCaptor.forClass(EntityCreateData.class);
            var entityId = EntityId.of(UUID.randomUUID());
            var personId = EntityId.of(UUID.randomUUID());
            Mockito.when(queryEngine.create(Mockito.any(), createDataCaptor.capture()))
                    .thenReturn(EntityData.builder().name(INVOICE.getName()).id(entityId).build());
            var result = datamodelApi.create(APPLICATION, INVOICE.getName(), MapRequestInputData.fromMap(Map.of(
                    "number", "invoice-1",
                    "amount", 1.50,
                    "received", Instant.now(clock),
                    "pay_before", Instant.now(clock).plus(30, ChronoUnit.DAYS),
                    "is_paid", false,
                    "customer", new RelationDataEntry(PERSON.getName(), personId)
            )));

            assertThat(result.getId()).isEqualTo(entityId);

            assertThat(createDataCaptor.getValue()).satisfies(createData -> {
                assertThat(createData.getEntityName()).isEqualTo(INVOICE.getName());
                assertThat(createData.getAttributes()).containsExactlyInAnyOrder(
                        new SimpleAttributeData<>(INVOICE_NUMBER.getName(), "invoice-1"),
                        new SimpleAttributeData<>(INVOICE_AMOUNT.getName(), BigDecimal.valueOf(1.50)),
                        new SimpleAttributeData<>(INVOICE_RECEIVED.getName(), Instant.now(clock)),
                        new SimpleAttributeData<>(INVOICE_PAY_BEFORE.getName(), Instant.now(clock).plus(30, ChronoUnit.DAYS)),
                        new SimpleAttributeData<>(INVOICE_IS_PAID.getName(), false),
                        CompositeAttributeData.builder()
                                .name(INVOICE_CONTENT.getName())
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getId().getName(), null))
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getFilename().getName(), null))
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getMimetype().getName(), null))
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getLength().getName(), null))
                                .build(),
                        getAuditMetadataData()
                );
                assertThat(createData.getRelations()).containsExactlyInAnyOrder(
                        XToOneRelationData.builder()
                                .name(INVOICE_CUSTOMER.getSourceEndPoint().getName())
                                .ref(personId)
                                .build()
                );
            });
        }

        @Test
        void missingRequiredProperties_fails() {
            assertThatThrownBy(() -> {
                datamodelApi.create(APPLICATION, INVOICE.getName(), MapRequestInputData.fromMap(Map.of(
                        "received", Instant.now(clock)
                )));
            }).isInstanceOfSatisfying(InvalidPropertyDataException.class, exception -> {
                assertThat(exception.allExceptions())
                        .allSatisfy(ex -> {
                            assertThat(ex.getCause()).isInstanceOf(RequiredConstraintViolationInvalidDataException.class);
                        })
                        .extracting(e -> String.join(".", e.getPath().toList()))
                        .containsExactlyInAnyOrder(
                                "amount",
                                "number",
                                "customer"
                        );
            });

            Mockito.verifyNoInteractions(queryEngine);
        }

        @Test
        void incorrectDataType_fails() {
            assertThatThrownBy(() -> {
                datamodelApi.create(APPLICATION, INVOICE.getName(), MapRequestInputData.fromMap(Map.of(
                        "number", 123,
                        "amount", Instant.now(clock),
                        "received", "abc",
                        "is_paid", "maybe",
                        "customer", "test123"
                )));
            }).isInstanceOfSatisfying(InvalidPropertyDataException.class, exception -> {
                assertThat(exception.allExceptions())
                        .allSatisfy(ex -> {
                            assertThat(ex.getCause()).isInstanceOf(InvalidDataTypeException.class);
                        })
                        .extracting(e -> String.join(".", e.getPath().toList()))
                        .containsExactlyInAnyOrder(
                                "number",
                                "amount",
                                "received",
                                "is_paid",
                                "customer"
                        );
            });

        }

        @Test
        void relations_succeeds() throws InvalidPropertyDataException {
            var createDataCaptor = ArgumentCaptor.forClass(EntityCreateData.class);
            var entityId = EntityId.of(UUID.randomUUID());
            var personId = EntityId.of(UUID.randomUUID());
            var productIds = List.of(EntityId.of(UUID.randomUUID()), EntityId.of(UUID.randomUUID()));
            Mockito.when(queryEngine.create(Mockito.any(), createDataCaptor.capture()))
                    .thenReturn(EntityData.builder().name(INVOICE.getName()).id(entityId).build());
            var result = datamodelApi.create(APPLICATION, INVOICE.getName(), MapRequestInputData.fromMap(Map.of(
                    "number", "invoice-1",
                    "amount", 1.50,
                    "customer", new RelationDataEntry(PERSON.getName(), personId),
                    "products", productIds.stream()
                            .map(pid -> new RelationDataEntry(PRODUCT.getName(), pid))
                            .toList()
            )));

            assertThat(result.getId()).isEqualTo(entityId);

            assertThat(createDataCaptor.getValue()).satisfies(createData -> {
                assertThat(createData.getEntityName()).isEqualTo(INVOICE.getName());
                assertThat(createData.getAttributes()).containsExactlyInAnyOrder(
                        new SimpleAttributeData<>(INVOICE_NUMBER.getName(), "invoice-1"),
                        new SimpleAttributeData<>(INVOICE_AMOUNT.getName(), BigDecimal.valueOf(1.50)),
                        new SimpleAttributeData<>(INVOICE_RECEIVED.getName(), null),
                        new SimpleAttributeData<>(INVOICE_PAY_BEFORE.getName(), null),
                        new SimpleAttributeData<>(INVOICE_IS_PAID.getName(), null),
                        CompositeAttributeData.builder()
                                .name(INVOICE_CONTENT.getName())
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getId().getName(), null))
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getFilename().getName(), null))
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getMimetype().getName(), null))
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getLength().getName(), null))
                                .build(),
                        getAuditMetadataData()
                );
                assertThat(createData.getRelations()).containsExactlyInAnyOrder(
                        XToOneRelationData.builder()
                                .name(RelationName.of("customer"))
                                .ref(personId)
                                .build(),
                        XToManyRelationData.builder()
                                .name(RelationName.of("products"))
                                .refs(productIds)
                                .build()
                );
            });

        }

        @Test
        void inverseRelation_unmapped_ignored() throws InvalidPropertyDataException {
            var createDataCaptor = ArgumentCaptor.forClass(EntityCreateData.class);
            var entityId = EntityId.of(UUID.randomUUID());
            Mockito.when(queryEngine.create(Mockito.any(), createDataCaptor.capture()))
                    .thenReturn(EntityData.builder().name(PERSON.getName()).id(entityId).build());
            var result = datamodelApi.create(APPLICATION, PERSON.getName(), MapRequestInputData.fromMap(Map.of(
                    "name", "test",
                    "vat", "123456"
                    // person also has a uni-directional "friends" relation that we don't provide here.
                    // The inverse relation is unnamed, so it should also not be processed
            )));

            assertThat(result.getId()).isEqualTo(entityId);

            assertThat(createDataCaptor.getValue()).satisfies(createData -> {
                assertThat(createData.getEntityName()).isEqualTo(PERSON.getName());
                assertThat(createData.getAttributes()).containsExactlyInAnyOrder(
                        new SimpleAttributeData<>(PERSON_NAME.getName(), "test"),
                        new SimpleAttributeData<>(PERSON_VAT.getName(), "123456"),
                        new SimpleAttributeData<>(PERSON_AGE.getName(), null)
                );

                assertThat(createData.getRelations()).isEmpty();
            });

        }

        @ParameterizedTest
        @MethodSource
        void incorrectRelation_fails(Object customer, Object products) {

            assertThatThrownBy(() -> {
                 datamodelApi.create(APPLICATION, INVOICE.getName(), MapRequestInputData.fromMap(Map.of(
                        "number", "invoice-1",
                        "amount", 1.50,
                        "customer", customer,
                        "products", products
                )));
            }).isInstanceOfSatisfying(InvalidPropertyDataException.class, exception -> {
                assertThat(exception.allExceptions())
                        .allSatisfy(ex -> {
                            assertThat(ex.getCause()).isInstanceOf(InvalidDataTypeException.class);
                        })
                        .extracting(e -> String.join(".", e.getPath().toList()))
                        .containsExactlyInAnyOrder(
                                "customer",
                                "products"
                        );
            });
        }

        static Stream<Arguments> incorrectRelation_fails() {
            var personId = EntityId.of(UUID.randomUUID());
            var productIds = List.of(EntityId.of(UUID.randomUUID()), EntityId.of(UUID.randomUUID()));
            return Stream.of(
                    Arguments.argumentSet("incorrect target entity", new RelationDataEntry(INVOICE.getName(), personId), productIds.stream()
                                .map(pid -> new RelationDataEntry(PERSON.getName(), pid))
                                .toList()),
                    Arguments.argumentSet("incorrect data type", "my-person", List.of(123456)),
                    Arguments.argumentSet("mixed up one/many", List.of(new RelationDataEntry(PERSON.getName(), personId)), new RelationDataEntry(PRODUCT.getName(), productIds.get(0)))
                    // TODO: re-enable when null-values are not considered valid for a to-many relation
                    //Arguments.argumentSet("incorrect empty value", List.of(), NullDataEntry.INSTANCE)
            );
        }

        @Test
        @Disabled("Content is not supported yet")
        void contentFile_succeeds() throws InvalidPropertyDataException {
            var createDataCaptor = ArgumentCaptor.forClass(EntityCreateData.class);
            var entityId = EntityId.of(UUID.randomUUID());
            var fileId = "my-file-123.bin";
            Mockito.when(queryEngine.create(Mockito.any(), createDataCaptor.capture()))
                    .thenReturn(EntityData.builder().name(INVOICE.getName()).id(entityId).build());

            var result = datamodelApi.create(APPLICATION, INVOICE.getName(), MapRequestInputData.fromMap(Map.of(
                    "number", "invoice-1",
                    "amount", 1.50,
                    "content", new FileDataEntry("my-file.pdf", "application/pdf", 120, InputStream::nullInputStream)
            )));

            assertThat(result.getId()).isEqualTo(entityId);

            assertThat(createDataCaptor.getValue()).satisfies(createData -> {
                assertThat(createData.getEntityName()).isEqualTo(INVOICE.getName());
                assertThat(createData.getAttributes()).containsExactlyInAnyOrder(
                        new SimpleAttributeData<>(INVOICE_NUMBER.getName(), "invoice-1"),
                        new SimpleAttributeData<>(INVOICE_AMOUNT.getName(), BigDecimal.valueOf(1.50)),
                        new SimpleAttributeData<>(INVOICE_RECEIVED.getName(), Instant.now(clock)),
                        new SimpleAttributeData<>(INVOICE_PAY_BEFORE.getName(), Instant.now(clock).plus(30, ChronoUnit.DAYS)),
                        new SimpleAttributeData<>(INVOICE_IS_PAID.getName(), false),
                        CompositeAttributeData.builder()
                                .name(INVOICE_CONTENT.getName())
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getId().getName(), fileId))
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getFilename().getName(), "my-file.pdf"))
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getMimetype().getName(), "application/pdf"))
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getLength().getName(), 120))
                                .build(),
                        getAuditMetadataData()
                );
                assertThat(createData.getRelations()).isEmpty();
            });
        }

        @Test
        @Disabled("Content is not supported yet")
        void contentAttributes_fails() {
            assertThatThrownBy(() -> datamodelApi.create(APPLICATION, INVOICE.getName(), MapRequestInputData.fromMap(Map.of(
                    "number", "invoice-1",
                    "amount", 1.50,
                    "content", Map.of(
                            "id", "123",
                            "filename", "test-file.pdf",
                            "mimetype", "application/pdf",
                            "length", 120
                    )
            ))))
                    .isInstanceOfSatisfying(InvalidPropertyDataException.class, e -> {
                        assertThat(e.getPath().toList()).isEqualTo(List.of("content"));
                    });

            Mockito.verifyNoInteractions(queryEngine);
        }
    }

    @Nested
    class UpdateEntity {

        @Test
        void allSimpleProperties_succeeds() throws InvalidPropertyDataException {
            var createDataCaptor = ArgumentCaptor.forClass(EntityData.class);
            var entityId = EntityId.of(UUID.randomUUID());
            var entity = EntityData.builder()
                    .name(INVOICE.getName())
                    .id(entityId)
                    .build();
            Mockito.when(queryEngine.update(Mockito.any(), createDataCaptor.capture()))
                    .thenReturn(new UpdateResult(entity, entity));
            datamodelApi.update(APPLICATION, INVOICE.getName(), entityId, MapRequestInputData.fromMap(Map.of(
                    "number", "invoice-1",
                    "amount", 1.50,
                    "received", Instant.now(clock),
                    "pay_before", NullDataEntry.INSTANCE, // Non-required value set to null
                    "is_paid", MissingDataEntry.INSTANCE // Non-required value is missing completely
            )));

            assertThat(createDataCaptor.getValue().getId()).isEqualTo(entityId);
            assertThat(createDataCaptor.getValue().getName()).isEqualTo(INVOICE.getName());
            assertThat(createDataCaptor.getValue().getAttributes()).containsExactlyInAnyOrder(
                    new SimpleAttributeData<>(INVOICE_NUMBER.getName(), "invoice-1"),
                    new SimpleAttributeData<>(INVOICE_AMOUNT.getName(), BigDecimal.valueOf(1.50)),
                    new SimpleAttributeData<>(INVOICE_RECEIVED.getName(), Instant.now(clock)),
                    new SimpleAttributeData<>(INVOICE_PAY_BEFORE.getName(), null), // Is set to null
                    new SimpleAttributeData<>(INVOICE_IS_PAID.getName(), null), // Is also set to null during an update
                    CompositeAttributeData.builder()
                            .name(INVOICE_CONTENT.getName())
                            .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getId().getName(), null))
                            .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getFilename().getName(), null))
                            .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getMimetype().getName(), null))
                            .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getLength().getName(), null))
                            .build(),
                    getAuditMetadataData()
            );
        }

        @Test
        void missingRequiredProperties_fails() {
            assertThatThrownBy(() -> {
                datamodelApi.update(APPLICATION, INVOICE.getName(), EntityId.of(UUID.randomUUID()),
                        MapRequestInputData.fromMap(Map.of(
                                "received", Instant.now(clock)
                        )));
            }).isInstanceOfSatisfying(InvalidPropertyDataException.class, exception -> {
                assertThat(exception.allExceptions())
                        .allSatisfy(ex -> {
                            assertThat(ex.getCause()).isInstanceOf(RequiredConstraintViolationInvalidDataException.class);
                        })
                        .extracting(e -> String.join(".", e.getPath().toList()))
                        .containsExactlyInAnyOrder(
                                "amount",
                                "number"
                        );
            });

            Mockito.verifyNoInteractions(queryEngine);
        }

        @Test
        @Disabled("Content is not supported yet")
        void contentAttributes_succeeds() throws InvalidPropertyDataException {
            var createDataCaptor = ArgumentCaptor.forClass(EntityData.class);
            var entityId = EntityId.of(UUID.randomUUID());
            var entity = EntityData.builder()
                    .name(INVOICE.getName())
                    .id(entityId)
                    .build();
            Mockito.when(queryEngine.update(Mockito.any(), createDataCaptor.capture()))
                    .thenReturn(new UpdateResult(entity, entity));
            datamodelApi.update(APPLICATION, INVOICE.getName(), entityId, MapRequestInputData.fromMap(Map.of(
                    "number", "invoice-1",
                    "amount", 1.50,
                    "content", Map.of(
                            "filename", "file-123.pdf",
                            "mimetype", "application/pdf",
                            "id", "will-be-ignored",
                            "length", 0xbad
                    )
            )));

            assertThat(createDataCaptor.getValue().getId()).isEqualTo(entityId);
            assertThat(createDataCaptor.getValue().getName()).isEqualTo(INVOICE.getName());
            assertThat(createDataCaptor.getValue().getAttributes()).containsExactlyInAnyOrder(
                    new SimpleAttributeData<>(INVOICE_NUMBER.getName(), "invoice-1"),
                    new SimpleAttributeData<>(INVOICE_AMOUNT.getName(), BigDecimal.valueOf(1.50)),
                    // Missing values are set to null
                    new SimpleAttributeData<>(INVOICE_RECEIVED.getName(), null),
                    new SimpleAttributeData<>(INVOICE_PAY_BEFORE.getName(), null),
                    new SimpleAttributeData<>(INVOICE_IS_PAID.getName(), null),
                    CompositeAttributeData.builder()
                            .name(INVOICE_CONTENT.getName())
                            // Note, content ID & length are not updated/overwritten ever
                            .attribute(
                                    new SimpleAttributeData<>(INVOICE_CONTENT.getFilename().getName(), "file-123.pdf"))
                            .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getMimetype().getName(),
                                    "application/pdf"))
                            .build(),
                    getAuditMetadataData()
            );
        }

        @Test
        @Disabled("Content is not supported yet")
        void contentFile_succeeds() throws InvalidPropertyDataException {

            var createDataCaptor = ArgumentCaptor.forClass(EntityData.class);
            var entityId = EntityId.of(UUID.randomUUID());
            var fileId = "my-file-123.bin";
            var entity = EntityData.builder()
                    .name(INVOICE.getName())
                    .id(entityId)
                    .build();
            Mockito.when(queryEngine.update(Mockito.any(), createDataCaptor.capture()))
                    .thenReturn(new UpdateResult(entity, entity));
            datamodelApi.update(APPLICATION, INVOICE.getName(), entityId, MapRequestInputData.fromMap(Map.of(
                    "number", "invoice-1",
                    "amount", 1.50,
                    "content", new FileDataEntry("file-123.pdf", "application/pdf", 120, InputStream::nullInputStream)
            )));

            assertThat(createDataCaptor.getValue().getId()).isEqualTo(entityId);
            assertThat(createDataCaptor.getValue().getName()).isEqualTo(INVOICE.getName());
            assertThat(createDataCaptor.getValue().getAttributes()).containsExactlyInAnyOrder(
                    new SimpleAttributeData<>(INVOICE_NUMBER.getName(), "invoice-1"),
                    new SimpleAttributeData<>(INVOICE_AMOUNT.getName(), BigDecimal.valueOf(1.50)),
                    // Missing values are set to null
                    new SimpleAttributeData<>(INVOICE_RECEIVED.getName(), null),
                    new SimpleAttributeData<>(INVOICE_PAY_BEFORE.getName(), null),
                    new SimpleAttributeData<>(INVOICE_IS_PAID.getName(), null),
                    CompositeAttributeData.builder()
                            .name(INVOICE_CONTENT.getName())
                            // New content ID is created and used
                            .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getId().getName(), fileId))
                            .attribute(
                                    new SimpleAttributeData<>(INVOICE_CONTENT.getFilename().getName(), "my-file.pdf"))
                            .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getMimetype().getName(),
                                    "application/pdf"))
                            .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getLength().getName(), 120))
                            .build(),
                    getAuditMetadataData()
            );
        }
    }

    @Nested
    class PartialUpdateEntity {

        @Test
        void allSimpleProperties_succeeds() throws InvalidPropertyDataException {
            var createDataCaptor = ArgumentCaptor.forClass(EntityData.class);
            var entityId = EntityId.of(UUID.randomUUID());
            var entity = EntityData.builder()
                    .name(INVOICE.getName())
                    .id(entityId)
                    .build();
            Mockito.when(queryEngine.update(Mockito.any(), createDataCaptor.capture()))
                    .thenReturn(new UpdateResult(entity, entity));
            datamodelApi.updatePartial(APPLICATION, INVOICE.getName(), entityId, MapRequestInputData.fromMap(Map.of(
                    "number", "invoice-1",
                    "amount", MissingDataEntry.INSTANCE, // Required value is missing completely
                    "received", Instant.now(clock),
                    "pay_before", NullDataEntry.INSTANCE, // Non-required value set to null
                    "is_paid", MissingDataEntry.INSTANCE // Non-required value is missing completely
            )));

            assertThat(createDataCaptor.getValue().getId()).isEqualTo(entityId);
            assertThat(createDataCaptor.getValue().getName()).isEqualTo(INVOICE.getName());
            assertThat(createDataCaptor.getValue().getAttributes()).containsExactlyInAnyOrder(
                    new SimpleAttributeData<>(INVOICE_NUMBER.getName(), "invoice-1"),
                    // amount is missing here, and thus not overwritten
                    new SimpleAttributeData<>(INVOICE_RECEIVED.getName(), Instant.now(clock)),
                    new SimpleAttributeData<>(INVOICE_PAY_BEFORE.getName(), null) // Is set to null
                    // is_paid is missing here, and thus not overwritten
            );
        }

        @Test
        void nullRequiredProperties_fails() {
            assertThatThrownBy(() -> {
                datamodelApi.updatePartial(APPLICATION, INVOICE.getName(), EntityId.of(UUID.randomUUID()),
                        MapRequestInputData.fromMap(Map.of(
                                "number", NullDataEntry.INSTANCE // Required value set to null
                        )));
            }).isInstanceOfSatisfying(InvalidPropertyDataException.class, exception -> {
                assertThat(exception.allExceptions())
                        .allSatisfy(ex -> {
                            assertThat(ex.getCause()).isInstanceOf(RequiredConstraintViolationInvalidDataException.class);
                        })
                        .extracting(e -> String.join(".", e.getPath().toList()))
                        .containsExactlyInAnyOrder(
                                "number"
                        );
            });

            Mockito.verifyNoInteractions(queryEngine);
        }

        @Test
        void nullRequiredRelation_ignored() throws InvalidPropertyDataException {
            var createDataCaptor = ArgumentCaptor.forClass(EntityData.class);
            var entityId = EntityId.of(UUID.randomUUID());
            var entity = EntityData.builder()
                    .name(INVOICE.getName())
                    .id(entityId)
                    .build();
            Mockito.when(queryEngine.update(Mockito.any(), createDataCaptor.capture()))
                    .thenReturn(new UpdateResult(entity, entity));
            datamodelApi.updatePartial(APPLICATION, INVOICE.getName(), entityId, MapRequestInputData.fromMap(Map.of(
                    "customer", NullDataEntry.INSTANCE // Relation is set to null; but updates do not affect relations
            )));

            assertThat(createDataCaptor.getValue().getId()).isEqualTo(entityId);
            assertThat(createDataCaptor.getValue().getName()).isEqualTo(INVOICE.getName());
            assertThat(createDataCaptor.getValue().getAttributes()).isEmpty();

        }

        @Test
        @Disabled("Content is not supported yet")
        void contentAttributes_succeeds() throws InvalidPropertyDataException {
            var createDataCaptor = ArgumentCaptor.forClass(EntityData.class);
            var entityId = EntityId.of(UUID.randomUUID());
            var entity = EntityData.builder()
                    .name(INVOICE.getName())
                    .id(entityId)
                    .build();
            Mockito.when(queryEngine.update(Mockito.any(), createDataCaptor.capture()))
                    .thenReturn(new UpdateResult(entity, entity));
            datamodelApi.updatePartial(APPLICATION, INVOICE.getName(), entityId, MapRequestInputData.fromMap(Map.of(
                    "content", Map.of(
                            "filename", "file-123.pdf",
                            "mimetype", MissingDataEntry.INSTANCE,
                            "id", "will-be-ignored",
                            "length", 0xbad
                    )
            )));

            assertThat(createDataCaptor.getValue().getId()).isEqualTo(entityId);
            assertThat(createDataCaptor.getValue().getName()).isEqualTo(INVOICE.getName());
            assertThat(createDataCaptor.getValue().getAttributes()).containsExactlyInAnyOrder(
                    CompositeAttributeData.builder()
                            .name(INVOICE_CONTENT.getName())
                            // Note, content ID & length are not updated/overwritten ever
                            .attribute(
                                    new SimpleAttributeData<>(INVOICE_CONTENT.getFilename().getName(), "file-123.pdf"))
                            // Mimetype is absent because it's a missing entry
                            .build()
            );
        }

        @Test
        @Disabled("Content is not supported yet")
        void contentFile_succeeds() throws InvalidPropertyDataException {

            var createDataCaptor = ArgumentCaptor.forClass(EntityData.class);
            var entityId = EntityId.of(UUID.randomUUID());
            var fileId = "my-file-123.bin";
            var entity = EntityData.builder()
                    .name(INVOICE.getName())
                    .id(entityId)
                    .build();
            Mockito.when(queryEngine.update(Mockito.any(), createDataCaptor.capture()))
                    .thenReturn(new UpdateResult(entity, entity));
            datamodelApi.update(APPLICATION, INVOICE.getName(), entityId, MapRequestInputData.fromMap(Map.of(
                    "content", new FileDataEntry("file-123.pdf", "application/pdf", 120, InputStream::nullInputStream)
            )));

            assertThat(createDataCaptor.getValue().getId()).isEqualTo(entityId);
            assertThat(createDataCaptor.getValue().getName()).isEqualTo(INVOICE.getName());
            assertThat(createDataCaptor.getValue().getAttributes()).containsExactlyInAnyOrder(
                    CompositeAttributeData.builder()
                            .name(INVOICE_CONTENT.getName())
                            // New content ID is created and used
                            .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getId().getName(), fileId))
                            .attribute(
                                    new SimpleAttributeData<>(INVOICE_CONTENT.getFilename().getName(), "my-file.pdf"))
                            .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getMimetype().getName(),
                                    "application/pdf"))
                            .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getLength().getName(), 120))
                            .build()
            );
        }
    }
}
