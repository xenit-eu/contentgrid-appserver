package com.contentgrid.appserver.domain;

import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.domain.data.DataEntry.FileDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.RelationDataEntry;
import com.contentgrid.appserver.domain.data.MapRequestInputData;
import com.contentgrid.appserver.domain.data.transformers.InvalidPropertyDataException;
import com.contentgrid.appserver.query.engine.api.QueryEngine;
import com.contentgrid.appserver.query.engine.api.UpdateResult;
import com.contentgrid.appserver.query.engine.api.data.CompositeAttributeData;
import com.contentgrid.appserver.query.engine.api.data.EntityCreateData;
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

    @BeforeEach
    void setup() {
        datamodelApi = new DatamodelApiImpl(queryEngine);
    }

    @Nested
    class CreateEntity {
        @Test
        void createWithAllSimpleProperties_succeeds() throws InvalidPropertyDataException {
            var createDataCaptor = ArgumentCaptor.forClass(EntityCreateData.class);
            var entityId = EntityId.of(UUID.randomUUID());
            Mockito.when(queryEngine.create(Mockito.any(), createDataCaptor.capture()))
                    .thenReturn(EntityData.builder().name(INVOICE.getName()).id(entityId).build());
            var result = datamodelApi.create(APPLICATION, INVOICE.getName(), MapRequestInputData.fromMap(Map.of(
                    "number", "invoice-1",
                    "amount", 1.50,
                    "received", Instant.now(clock),
                    "pay_before", Instant.now(clock).plus(30, ChronoUnit.DAYS),
                    "is_paid", false
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
                assertThat(createData.getRelations()).isEmpty();
            });
        }

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

        @Test
        void createMissingRequiredProperties_fails() {
            assertThatThrownBy(() -> {
                datamodelApi.create(APPLICATION, INVOICE.getName(), MapRequestInputData.fromMap(Map.of(
                        "received", Instant.now(clock)
                )));
            }).isInstanceOfSatisfying(InvalidPropertyDataException.class, exception -> {
                assertThat(exception.allExceptions())
                        .extracting(e -> String.join(".", e.getPath().toList()))
                        .containsExactlyInAnyOrder(
                                "number"
                        );
            });

            Mockito.verifyNoInteractions(queryEngine);
        }

        @Test
        void createIncorrectDataType_fails() {
            assertThatThrownBy(() -> {
                datamodelApi.create(APPLICATION, INVOICE.getName(), MapRequestInputData.fromMap(Map.of(
                        "number", 123,
                        "amount", Instant.now(clock),
                        "received", "abc",
                        "is_paid", "maybe"
                )));
            }).isInstanceOfSatisfying(InvalidPropertyDataException.class, exception -> {
                assertThat(exception.allExceptions())
                        .extracting(e -> String.join(".", e.getPath().toList()))
                        .containsExactlyInAnyOrder(
                                "number",
                                "amount",
                                "received",
                                "is_paid"
                        );
            });

        }

        @Test
        void createWithRelations_succeeds() throws InvalidPropertyDataException {
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

        @ParameterizedTest
        @MethodSource
        void createWithIncorrectRelation_fails(Object customer, Object products) {

            assertThatThrownBy(() -> {
                 datamodelApi.create(APPLICATION, INVOICE.getName(), MapRequestInputData.fromMap(Map.of(
                        "number", "invoice-1",
                        "amount", 1.50,
                        "customer", customer,
                        "products", products
                )));
            }).isInstanceOfSatisfying(InvalidPropertyDataException.class, exception -> {
                assertThat(exception.allExceptions())
                        .extracting(e -> String.join(".", e.getPath().toList()))
                        .containsExactlyInAnyOrder(
                                "customer",
                                "products"
                        );
            });
        }

        static Stream<Arguments> createWithIncorrectRelation_fails() {
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
        void createWithContent_succeeds() throws InvalidPropertyDataException {
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
        void createWithContentAttributes_fails() {
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


}