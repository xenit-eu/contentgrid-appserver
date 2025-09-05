package com.contentgrid.appserver.domain;

import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.SortableName;
import com.contentgrid.appserver.content.api.ContentReference;
import com.contentgrid.appserver.content.api.ContentStore;
import com.contentgrid.appserver.content.api.ContentWriter;
import com.contentgrid.appserver.content.api.UnwritableContentException;
import com.contentgrid.appserver.domain.authorization.PermissionPredicate;
import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.FileDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MissingDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.NullDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.RelationDataEntry;
import com.contentgrid.appserver.domain.data.InvalidDataTypeException;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.domain.data.MapRequestInputData;
import com.contentgrid.appserver.domain.data.validation.ContentMissingInvalidDataException;
import com.contentgrid.appserver.domain.data.validation.RequiredConstraintViolationInvalidDataException;
import com.contentgrid.appserver.domain.values.ItemCount;
import com.contentgrid.appserver.domain.paging.PageBasedPagination;
import com.contentgrid.appserver.domain.paging.cursor.CursorCodec;
import com.contentgrid.appserver.domain.paging.cursor.CursorCodec.CursorContext;
import com.contentgrid.appserver.domain.paging.cursor.EncodedCursorPagination;
import com.contentgrid.appserver.domain.paging.cursor.RequestIntegrityCheckCursorCodec;
import com.contentgrid.appserver.domain.paging.cursor.SimplePageBasedCursorCodec;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.domain.values.EntityRequest;
import com.contentgrid.appserver.query.engine.api.QueryEngine;
import com.contentgrid.appserver.query.engine.api.UpdateResult;
import com.contentgrid.appserver.query.engine.api.data.CompositeAttributeData;
import com.contentgrid.appserver.query.engine.api.data.EntityCreateData;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.OffsetData;
import com.contentgrid.appserver.query.engine.api.data.QueryPageData;
import com.contentgrid.appserver.query.engine.api.data.SimpleAttributeData;
import com.contentgrid.appserver.query.engine.api.data.SliceData;
import com.contentgrid.appserver.query.engine.api.data.SortData;
import com.contentgrid.appserver.query.engine.api.data.SortData.Direction;
import com.contentgrid.appserver.query.engine.api.data.SortData.FieldSort;
import com.contentgrid.appserver.query.engine.api.data.XToManyRelationData;
import com.contentgrid.appserver.query.engine.api.data.XToOneRelationData;
import com.contentgrid.appserver.query.engine.api.exception.EntityIdNotFoundException;
import com.contentgrid.appserver.query.engine.api.thunx.expression.StringComparison;
import com.contentgrid.hateoas.pagination.api.Pagination;
import com.contentgrid.thunx.predicates.model.LogicalOperation;
import com.contentgrid.thunx.predicates.model.Scalar;
import com.contentgrid.thunx.predicates.model.SymbolicReference;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class DatamodelApiImplTest {
    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    private QueryEngine queryEngine;
    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    private ContentStore contentStore;
    @Spy
    private CursorCodec codec = new RequestIntegrityCheckCursorCodec(new SimplePageBasedCursorCodec());

    private DatamodelApi datamodelApi;

    private final Clock clock = Clock.fixed(Instant.ofEpochSecond(440991035), ZoneOffset.UTC);

    @BeforeEach
    void setup() {
        datamodelApi = new DatamodelApiImpl(
                queryEngine,
                contentStore,
                codec
        );
    }

    void setupEntityQuery() {
        Mockito.when(queryEngine.findById(Mockito.any(), Mockito.any(), Mockito.any())).then(args -> {
            var request = args.getArgument(1, EntityRequest.class);

            return Optional.of(
                    new EntityData(
                            EntityIdentity.forEntity(request.getEntityName(), request.getEntityId()),
                            List.of()
                    )
            );
        });
    }

    void setupEntityQueryWithContent(String contentId) {
        Mockito.when(queryEngine.findById(Mockito.any(), Mockito.any(), Mockito.any())).then(args -> {
            var request = args.getArgument(1, EntityRequest.class);
            return Optional.of(
                    new EntityData(
                            EntityIdentity.forEntity(request.getEntityName(), request.getEntityId()),
                            List.of(
                                    CompositeAttributeData.builder()
                                            .name(INVOICE_CONTENT.getName())
                                            .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getId().getName(),
                                                    contentId))
                                            .build()
                            )
                    )
            );
        });

    }

    @Nested
    class CreateEntity {
        @Test
        void allSimpleProperties_succeeds() throws InvalidPropertyDataException {
            var createDataCaptor = ArgumentCaptor.forClass(EntityCreateData.class);
            var entityId = EntityId.of(UUID.randomUUID());
            var personId = EntityId.of(UUID.randomUUID());
            Mockito.when(queryEngine.create(Mockito.any(), createDataCaptor.capture(), Mockito.any()))
                    .thenReturn(EntityData.builder().name(INVOICE.getName()).id(entityId).build());
            var result = datamodelApi.create(APPLICATION, INVOICE.getName(), MapRequestInputData.fromMap(Map.of(
                            "number", "invoice-1",
                            "amount", 1.50,
                            "received", Instant.now(clock),
                            "pay_before", Instant.now(clock).plus(30, ChronoUnit.DAYS),
                            "is_paid", false,
                            "confidentiality", "public",
                            "customer", new RelationDataEntry(PERSON.getName(), personId)
                    )),
                    PermissionPredicate.allowAll()
            );

            assertThat(result.getId()).isEqualTo(entityId);

            assertThat(createDataCaptor.getValue()).satisfies(createData -> {
                assertThat(createData.getEntityName()).isEqualTo(INVOICE.getName());
                assertThat(createData.getAttributes()).containsExactlyInAnyOrder(
                        new SimpleAttributeData<>(INVOICE_NUMBER.getName(), "invoice-1"),
                        new SimpleAttributeData<>(INVOICE_AMOUNT.getName(), BigDecimal.valueOf(1.50)),
                        new SimpleAttributeData<>(INVOICE_RECEIVED.getName(), Instant.now(clock)),
                        new SimpleAttributeData<>(INVOICE_PAY_BEFORE.getName(), Instant.now(clock).plus(30, ChronoUnit.DAYS)),
                        new SimpleAttributeData<>(INVOICE_IS_PAID.getName(), false),
                        new SimpleAttributeData<>(INVOICE_CONFIDENTIALITY.getName(), "public"),
                        CompositeAttributeData.builder()
                                .name(INVOICE_CONTENT.getName())
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getId().getName(), null))
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getFilename().getName(), null))
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getMimetype().getName(), null))
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getLength().getName(), null))
                                .build()
                );
                assertThat(createData.getRelations()).containsExactlyInAnyOrder(
                        XToOneRelationData.builder()
                                .name(INVOICE_CUSTOMER.getSourceEndPoint().getName())
                                .ref(personId)
                                .build()
                );
            });

            Mockito.verifyNoInteractions(contentStore);
        }

        @Test
        void missingRequiredProperties_fails() {
            assertThatThrownBy(() -> {
                datamodelApi.create(APPLICATION, INVOICE.getName(), MapRequestInputData.fromMap(Map.of(
                        "received", Instant.now(clock),
                        "confidentiality", "public"
                )), PermissionPredicate.allowAll());
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

            Mockito.verifyNoInteractions(queryEngine, contentStore);
        }

        @Test
        void incorrectDataType_fails() {
            assertThatThrownBy(() -> {
                datamodelApi.create(APPLICATION, INVOICE.getName(), MapRequestInputData.fromMap(Map.of(
                        "number", 123,
                        "amount", Instant.now(clock),
                        "received", "abc",
                        "is_paid", "maybe",
                        "confidentiality", "public",
                        "customer", "test123"
                )), PermissionPredicate.allowAll());
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

            Mockito.verifyNoInteractions(queryEngine, contentStore);

        }

        @Test
        void relations_succeeds() throws InvalidPropertyDataException {
            var createDataCaptor = ArgumentCaptor.forClass(EntityCreateData.class);
            var entityId = EntityId.of(UUID.randomUUID());
            var personId = EntityId.of(UUID.randomUUID());
            var productIds = List.of(EntityId.of(UUID.randomUUID()), EntityId.of(UUID.randomUUID()));
            Mockito.when(queryEngine.create(Mockito.any(), createDataCaptor.capture(), Mockito.any()))
                    .thenReturn(EntityData.builder().name(INVOICE.getName()).id(entityId).build());

            var result = datamodelApi.create(APPLICATION, INVOICE.getName(), MapRequestInputData.fromMap(Map.of(
                            "number", "invoice-1",
                            "amount", 1.50,
                            "confidentiality", "public",
                            "customer", new RelationDataEntry(PERSON.getName(), personId),
                            "products", productIds.stream()
                                    .map(pid -> new RelationDataEntry(PRODUCT.getName(), pid))
                                    .toList()
                    )),
                    PermissionPredicate.allowAll()
            );

            assertThat(result.getId()).isEqualTo(entityId);

            assertThat(createDataCaptor.getValue()).satisfies(createData -> {
                assertThat(createData.getEntityName()).isEqualTo(INVOICE.getName());
                assertThat(createData.getAttributes()).containsExactlyInAnyOrder(
                        new SimpleAttributeData<>(INVOICE_NUMBER.getName(), "invoice-1"),
                        new SimpleAttributeData<>(INVOICE_AMOUNT.getName(), BigDecimal.valueOf(1.50)),
                        new SimpleAttributeData<>(INVOICE_CONFIDENTIALITY.getName(), "public"),
                        new SimpleAttributeData<>(INVOICE_RECEIVED.getName(), null),
                        new SimpleAttributeData<>(INVOICE_PAY_BEFORE.getName(), null),
                        new SimpleAttributeData<>(INVOICE_IS_PAID.getName(), null),
                        CompositeAttributeData.builder()
                                .name(INVOICE_CONTENT.getName())
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getId().getName(), null))
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getFilename().getName(), null))
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getMimetype().getName(), null))
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getLength().getName(), null))
                                .build()
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

            Mockito.verifyNoInteractions(contentStore);
        }

        @Test
        void hidden_relation_ignored() throws InvalidPropertyDataException {
            var createDataCaptor = ArgumentCaptor.forClass(EntityCreateData.class);
            var entityId = EntityId.of(UUID.randomUUID());
            var personId = EntityId.of(UUID.randomUUID());
            Mockito.when(queryEngine.create(Mockito.any(), createDataCaptor.capture(), Mockito.any()))
                    .thenReturn(EntityData.builder().name(PERSON.getName()).id(entityId).build());
            var result = datamodelApi.create(APPLICATION, PERSON.getName(), MapRequestInputData.fromMap(Map.of(
                            "name", "Test person",
                            "vat", "XXXX",
                            "friends", List.of(new DataEntry.RelationDataEntry(
                                    PERSON.getName(),
                                    personId
                            )),
                            "__inverse_friends", List.of(new RelationDataEntry(
                                    PERSON.getName(),
                                    personId
                            ))
                    )),
                    PermissionPredicate.allowAll()
            );

            assertThat(result.getId()).isEqualTo(entityId);
            assertThat(createDataCaptor.getValue()).satisfies(createData -> {
                assertThat(createData.getRelations()).containsExactlyInAnyOrder(
                        XToManyRelationData.builder()
                                .name(RelationName.of("friends"))
                                .ref(personId)
                                .build()
                        // Note: __inverse_friends is not present here
                );
            });
        }

        @Test
        void inverseRelation_unmapped_ignored() throws InvalidPropertyDataException {
            var createDataCaptor = ArgumentCaptor.forClass(EntityCreateData.class);
            var entityId = EntityId.of(UUID.randomUUID());
            Mockito.when(queryEngine.create(Mockito.any(), createDataCaptor.capture(), Mockito.any()))
                    .thenReturn(EntityData.builder().name(PERSON.getName()).id(entityId).build());
            var result = datamodelApi.create(APPLICATION, PERSON.getName(), MapRequestInputData.fromMap(Map.of(
                    "name", "test",
                    "vat", "123456"
                    // person also has a uni-directional "friends" relation that we don't provide here.
                    // The inverse relation is unnamed, so it should also not be processed
            )), PermissionPredicate.allowAll());

            assertThat(result.getId()).isEqualTo(entityId);

            assertThat(createDataCaptor.getValue()).satisfies(createData -> {
                assertThat(createData.getEntityName()).isEqualTo(PERSON.getName());
                assertThat(createData.getAttributes()).containsExactlyInAnyOrder(
                        new SimpleAttributeData<>(PERSON_NAME.getName(), "test"),
                        new SimpleAttributeData<>(PERSON_VAT.getName(), "123456"),
                        new SimpleAttributeData<>(PERSON_AGE.getName(), null),
                        new SimpleAttributeData<>(PERSON_GENDER.getName(), null)
                );

                assertThat(createData.getRelations()).isEmpty();
            });

            Mockito.verifyNoInteractions(contentStore);
        }

        @ParameterizedTest
        @MethodSource
        void incorrectRelation_fails(Object customer, Object products) {

            assertThatThrownBy(() -> {
                 datamodelApi.create(APPLICATION, INVOICE.getName(), MapRequestInputData.fromMap(Map.of(
                        "number", "invoice-1",
                        "amount", 1.50,
                        "confidentiality", "public",
                        "customer", customer,
                        "products", products
                 )), PermissionPredicate.allowAll());
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

            Mockito.verifyNoInteractions(queryEngine, contentStore);
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
        void contentFile_succeeds() throws InvalidPropertyDataException, UnwritableContentException {
            var createDataCaptor = ArgumentCaptor.forClass(EntityCreateData.class);
            var entityId = EntityId.of(UUID.randomUUID());
            var personId = EntityId.of(UUID.randomUUID());
            var fileId = "my-file-123.bin";
            Mockito.when(queryEngine.create(Mockito.any(), createDataCaptor.capture(), Mockito.any()))
                    .thenReturn(EntityData.builder().name(INVOICE.getName()).id(entityId).build());
            Mockito.when(contentStore.createNewWriter()).thenAnswer(contentWriterFor(fileId, 110));

            var result = datamodelApi.create(APPLICATION, INVOICE.getName(), MapRequestInputData.fromMap(Map.of(
                    "number", "invoice-1",
                    "amount", 1.50,
                    "confidentiality", "public",
                    "customer", new RelationDataEntry(PERSON.getName(), personId),
                    "content", new FileDataEntry("my-file.pdf", "application/pdf", InputStream::nullInputStream)
            )), PermissionPredicate.allowAll());

            assertThat(result.getId()).isEqualTo(entityId);

            assertThat(createDataCaptor.getValue()).satisfies(createData -> {
                assertThat(createData.getEntityName()).isEqualTo(INVOICE.getName());
                assertThat(createData.getAttributes()).containsExactlyInAnyOrder(
                        new SimpleAttributeData<>(INVOICE_NUMBER.getName(), "invoice-1"),
                        new SimpleAttributeData<>(INVOICE_AMOUNT.getName(), BigDecimal.valueOf(1.50)),
                        new SimpleAttributeData<>(INVOICE_RECEIVED.getName(), null),
                        new SimpleAttributeData<>(INVOICE_PAY_BEFORE.getName(), null),
                        new SimpleAttributeData<>(INVOICE_IS_PAID.getName(), null),
                        new SimpleAttributeData<>(INVOICE_CONFIDENTIALITY.getName(), "public"),
                        CompositeAttributeData.builder()
                                .name(INVOICE_CONTENT.getName())
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getId().getName(), fileId))
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getFilename().getName(), "my-file.pdf"))
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getMimetype().getName(), "application/pdf"))
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getLength().getName(), 110L))
                                .build()
                );
                assertThat(createData.getRelations()).containsExactlyInAnyOrder(
                        XToOneRelationData.builder()
                                .name(RelationName.of("customer"))
                                .ref(personId)
                                .build()
                );
            });
        }

        @Test
        void contentAttributes_fails() {
            var personId = EntityId.of(UUID.randomUUID());
            assertThatThrownBy(() -> datamodelApi.create(APPLICATION, INVOICE.getName(), MapRequestInputData.fromMap(Map.of(
                    "number", "invoice-1",
                    "amount", 1.50,
                    "confidentiality", "public",
                    "customer", new RelationDataEntry(PERSON.getName(), personId),
                    "content", Map.of(
                            "id", "123",
                            "filename", "test-file.pdf",
                            "mimetype", "application/pdf",
                            "length", 120
                    )
            )), PermissionPredicate.allowAll()))
                    .isInstanceOfSatisfying(InvalidPropertyDataException.class, e -> {
                        assertThat(e.getPath().toList()).isEqualTo(List.of("content"));
                    });

            Mockito.verifyNoInteractions(contentStore, queryEngine);
        }
    }

    private static Answer<ContentWriter> contentWriterFor(String fileId, long size) {
        return invocation -> {
            var cw = Mockito.mock(ContentWriter.class, Answers.RETURNS_SMART_NULLS);
            Mockito.when(cw.getContentOutputStream()).thenReturn(OutputStream.nullOutputStream());
            Mockito.when(cw.getContentSize()).thenReturn(size);
            Mockito.when(cw.getReference()).thenReturn(ContentReference.of(fileId));
            return cw;
        };
    }

    @Nested
    class UpdateEntity {

        @Test
        void allSimpleProperties_succeeds() throws InvalidPropertyDataException {
            setupEntityQuery();
            var createDataCaptor = ArgumentCaptor.forClass(EntityData.class);
            var entityId = EntityId.of(UUID.randomUUID());
            var entity = EntityData.builder()
                    .name(INVOICE.getName())
                    .id(entityId)
                    .build();
            Mockito.when(queryEngine.update(Mockito.any(), createDataCaptor.capture(), Mockito.any()))
                    .thenReturn(new UpdateResult(entity, entity));
            datamodelApi.update(APPLICATION, EntityRequest.forEntity(INVOICE.getName(), entityId),
                    MapRequestInputData.fromMap(Map.of(
                            "number", "invoice-1",
                            "amount", 1.50,
                            "received", Instant.now(clock),
                            "confidentiality", "public",
                            "pay_before", NullDataEntry.INSTANCE, // Non-required value set to null
                            "is_paid", MissingDataEntry.INSTANCE // Non-required value is missing completely
                    )),
                    PermissionPredicate.allowAll()
            );

            assertThat(createDataCaptor.getValue().getId()).isEqualTo(entityId);
            assertThat(createDataCaptor.getValue().getName()).isEqualTo(INVOICE.getName());
            assertThat(createDataCaptor.getValue().getAttributes()).containsExactlyInAnyOrder(
                    new SimpleAttributeData<>(INVOICE_NUMBER.getName(), "invoice-1"),
                    new SimpleAttributeData<>(INVOICE_AMOUNT.getName(), BigDecimal.valueOf(1.50)),
                    new SimpleAttributeData<>(INVOICE_RECEIVED.getName(), Instant.now(clock)),
                    new SimpleAttributeData<>(INVOICE_CONFIDENTIALITY.getName(), "public"),
                    new SimpleAttributeData<>(INVOICE_PAY_BEFORE.getName(), null), // Is set to null
                    new SimpleAttributeData<>(INVOICE_IS_PAID.getName(), null), // Is also set to null during an update
                    CompositeAttributeData.builder()
                            .name(INVOICE_CONTENT.getName())
                            .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getId().getName(), null))
                            .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getFilename().getName(), null))
                            .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getMimetype().getName(), null))
                            .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getLength().getName(), null))
                            .build()
            );

            Mockito.verifyNoInteractions(contentStore);
        }

        @Test
        void missingRequiredProperties_fails() {
            setupEntityQuery();
            assertThatThrownBy(() -> {
                datamodelApi.update(APPLICATION,
                        EntityRequest.forEntity(INVOICE.getName(), EntityId.of(UUID.randomUUID())),
                        MapRequestInputData.fromMap(Map.of(
                                "received", Instant.now(clock),
                                "confidentiality", "public"
                        )),
                        PermissionPredicate.allowAll()
                );
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

            Mockito.verify(queryEngine, Mockito.never()).update(Mockito.any(), Mockito.any(), Mockito.any());
            Mockito.verifyNoInteractions(contentStore);
        }

        @Test
        void contentAttributes_succeeds() throws InvalidPropertyDataException {
            var createDataCaptor = ArgumentCaptor.forClass(EntityData.class);
            var entityId = EntityId.of(UUID.randomUUID());
            var entity = EntityData.builder()
                    .name(INVOICE.getName())
                    .id(entityId)
                    .build();
            setupEntityQueryWithContent("content.bin");
            Mockito.when(queryEngine.update(Mockito.any(), createDataCaptor.capture(), Mockito.any()))
                    .thenReturn(new UpdateResult(entity, entity));
            datamodelApi.update(APPLICATION, EntityRequest.forEntity(INVOICE.getName(), entityId),
                    MapRequestInputData.fromMap(Map.of(
                            "number", "invoice-1",
                            "amount", 1.50,
                            "confidentiality", "public",
                            "content", Map.of(
                                    "filename", "file-123.pdf",
                                    "mimetype", "application/pdf",
                                    "id", "will-be-ignored",
                                    "length", 0xbad
                            )
                    )),
                    PermissionPredicate.allowAll()
            );

            assertThat(createDataCaptor.getValue().getId()).isEqualTo(entityId);
            assertThat(createDataCaptor.getValue().getName()).isEqualTo(INVOICE.getName());
            assertThat(createDataCaptor.getValue().getAttributes()).containsExactlyInAnyOrder(
                    new SimpleAttributeData<>(INVOICE_NUMBER.getName(), "invoice-1"),
                    new SimpleAttributeData<>(INVOICE_AMOUNT.getName(), BigDecimal.valueOf(1.50)),
                    new SimpleAttributeData<>(INVOICE_CONFIDENTIALITY.getName(), "public"),
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
                            .build()
            );
        }

        @Test
        void contentAttributes_withoutContent_fails() {
            var entityId = EntityId.of(UUID.randomUUID());
            setupEntityQueryWithContent(null);
            assertThatThrownBy(() -> {
                datamodelApi.update(APPLICATION, EntityRequest.forEntity(INVOICE.getName(), entityId),
                        MapRequestInputData.fromMap(Map.of(
                                "number", "invoice-1",
                                "amount", 1.50,
                                "content", Map.of(
                                        "filename", "file-123.pdf",
                                        "mimetype", "application/pdf",
                                        "id", "will-be-ignored",
                                        "length", 0xbad
                                )
                        )),
                        PermissionPredicate.allowAll()
                );
            }).isInstanceOfSatisfying(InvalidPropertyDataException.class, exception -> {
                assertThat(exception.allExceptions())
                        .anySatisfy(ex -> {
                            assertThat(ex.getCause()).isInstanceOf(ContentMissingInvalidDataException.class);
                            assertThat(ex.getPath().toList()).isEqualTo(List.of("content"));
                        });
            });
        }

        static Stream<DataEntry> missingAndNullDataEntry() {
            return Stream.of(MissingDataEntry.INSTANCE, NullDataEntry.INSTANCE);
        }

        @ParameterizedTest
        @MethodSource("missingAndNullDataEntry")
        void contentAttributes_missingMimetype_fails(DataEntry dataEntry) {
            setupEntityQueryWithContent("content.bin");
            var entityId = EntityId.of(UUID.randomUUID());
            assertThatThrownBy(() -> {
                datamodelApi.update(APPLICATION, EntityRequest.forEntity(INVOICE.getName(), entityId),
                        MapRequestInputData.fromMap(Map.of(
                                "number", "invoice-1",
                                "amount", 1.50,
                                "content", Map.of(
                                        "filename", "file-123.pdf",
                                        "mimetype", dataEntry
                                )
                        )),
                        PermissionPredicate.allowAll()
                );
            }).isInstanceOfSatisfying(InvalidPropertyDataException.class, exception -> {
                assertThat(exception.allExceptions())
                        .anySatisfy(ex -> {
                            assertThat(ex.getCause()).isInstanceOf(RequiredConstraintViolationInvalidDataException.class);
                            assertThat(ex.getPath().toList()).isEqualTo(List.of("content", "mimetype"));
                        });
            });
        }

        @ParameterizedTest
        @MethodSource("missingAndNullDataEntry")
        void contentAttributes_missingFilename_succeeds(DataEntry dataEntry) throws InvalidPropertyDataException {
            setupEntityQueryWithContent("content.bin");
            var entityId = EntityId.of(UUID.randomUUID());
            var createDataCaptor = ArgumentCaptor.forClass(EntityData.class);
            var entity = EntityData.builder()
                    .name(INVOICE.getName())
                    .id(entityId)
                    .build();
            Mockito.when(queryEngine.update(Mockito.any(), createDataCaptor.capture(), Mockito.any()))
                    .thenReturn(new UpdateResult(entity, entity));
            datamodelApi.update(APPLICATION, EntityRequest.forEntity(INVOICE.getName(), entityId),
                    MapRequestInputData.fromMap(Map.of(
                            "number", "invoice-1",
                            "amount", 1.50,
                            "confidentiality", "public",
                            "content", Map.of(
                                    "filename", dataEntry,
                                    "mimetype", "application/pdf"
                            )
                    )),
                    PermissionPredicate.allowAll()
            );

            assertThat(createDataCaptor.getValue().getId()).isEqualTo(entityId);
            assertThat(createDataCaptor.getValue().getName()).isEqualTo(INVOICE.getName());
            assertThat(createDataCaptor.getValue().getAttributes()).containsExactlyInAnyOrder(
                    new SimpleAttributeData<>(INVOICE_NUMBER.getName(), "invoice-1"),
                    new SimpleAttributeData<>(INVOICE_AMOUNT.getName(), BigDecimal.valueOf(1.50)),
                    new SimpleAttributeData<>(INVOICE_CONFIDENTIALITY.getName(), "public"),
                    // Missing values are set to null
                    new SimpleAttributeData<>(INVOICE_RECEIVED.getName(), null),
                    new SimpleAttributeData<>(INVOICE_PAY_BEFORE.getName(), null),
                    new SimpleAttributeData<>(INVOICE_IS_PAID.getName(), null),
                    CompositeAttributeData.builder()
                            .name(INVOICE_CONTENT.getName())
                            // Content ID and size are kept/not overwritten, so they are not present here
                            .attribute(
                                    new SimpleAttributeData<>(INVOICE_CONTENT.getFilename().getName(), null))
                            .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getMimetype().getName(),
                                    "application/pdf"))
                            .build()
            );
        }

        @Test
        void contentFile_succeeds() throws InvalidPropertyDataException, UnwritableContentException {

            setupEntityQuery();
            var createDataCaptor = ArgumentCaptor.forClass(EntityData.class);
            var entityId = EntityId.of(UUID.randomUUID());
            var fileId = "my-file-123.bin";
            var entity = EntityData.builder()
                    .name(INVOICE.getName())
                    .id(entityId)
                    .build();
            Mockito.when(queryEngine.update(Mockito.any(), createDataCaptor.capture(), Mockito.any()))
                    .thenReturn(new UpdateResult(entity, entity));

            Mockito.when(contentStore.createNewWriter()).thenAnswer(contentWriterFor(fileId, 50));

            datamodelApi.update(APPLICATION, EntityRequest.forEntity(INVOICE.getName(), entityId),
                    MapRequestInputData.fromMap(Map.of(
                            "number", "invoice-1",
                            "amount", 1.50,
                            "confidentiality", "public",
                            "content", new FileDataEntry("my-file.pdf", "application/pdf", InputStream::nullInputStream)
                    )),
                    PermissionPredicate.allowAll()
            );

            assertThat(createDataCaptor.getValue().getId()).isEqualTo(entityId);
            assertThat(createDataCaptor.getValue().getName()).isEqualTo(INVOICE.getName());
            assertThat(createDataCaptor.getValue().getAttributes()).containsExactlyInAnyOrder(
                    new SimpleAttributeData<>(INVOICE_NUMBER.getName(), "invoice-1"),
                    new SimpleAttributeData<>(INVOICE_AMOUNT.getName(), BigDecimal.valueOf(1.50)),
                    new SimpleAttributeData<>(INVOICE_CONFIDENTIALITY.getName(), "public"),
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
                            .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getLength().getName(), 50L))
                            .build()
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

            setupEntityQuery();
            Mockito.when(queryEngine.update(Mockito.any(), createDataCaptor.capture(), Mockito.any()))
                    .thenReturn(new UpdateResult(entity, entity));
            datamodelApi.updatePartial(APPLICATION, EntityRequest.forEntity(INVOICE.getName(), entityId),
                    MapRequestInputData.fromMap(Map.of(
                            "number", "invoice-1",
                            "amount", MissingDataEntry.INSTANCE, // Required value is missing completely
                            "confidentiality", "public",
                            "received", Instant.now(clock),
                            "pay_before", NullDataEntry.INSTANCE, // Non-required value set to null
                            "is_paid", MissingDataEntry.INSTANCE // Non-required value is missing completely
                    )),
                    PermissionPredicate.allowAll()
            );

            assertThat(createDataCaptor.getValue().getId()).isEqualTo(entityId);
            assertThat(createDataCaptor.getValue().getName()).isEqualTo(INVOICE.getName());
            assertThat(createDataCaptor.getValue().getAttributes()).containsExactlyInAnyOrder(
                    new SimpleAttributeData<>(INVOICE_NUMBER.getName(), "invoice-1"),
                    // amount is missing here, and thus not overwritten
                    new SimpleAttributeData<>(INVOICE_CONFIDENTIALITY.getName(), "public"),
                    new SimpleAttributeData<>(INVOICE_RECEIVED.getName(), Instant.now(clock)),
                    new SimpleAttributeData<>(INVOICE_PAY_BEFORE.getName(), null) // Is set to null
                    // is_paid is missing here, and thus not overwritten
            );

            Mockito.verifyNoInteractions(contentStore);
        }

        @Test
        void nullRequiredProperties_fails() {
            setupEntityQuery();
            assertThatThrownBy(() -> {
                datamodelApi.updatePartial(APPLICATION,
                        EntityRequest.forEntity(INVOICE.getName(), EntityId.of(UUID.randomUUID())),
                        MapRequestInputData.fromMap(Map.of(
                                "number", NullDataEntry.INSTANCE // Required value set to null
                        )), PermissionPredicate.allowAll());
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

            Mockito.verify(queryEngine, Mockito.never()).update(Mockito.any(), Mockito.any(), Mockito.any());
            Mockito.verifyNoInteractions(contentStore);
        }

        @Test
        void nullRequiredRelation_ignored() throws InvalidPropertyDataException {
            setupEntityQuery();
            var createDataCaptor = ArgumentCaptor.forClass(EntityData.class);
            var entityId = EntityId.of(UUID.randomUUID());
            var entity = EntityData.builder()
                    .name(INVOICE.getName())
                    .id(entityId)
                    .build();
            Mockito.when(queryEngine.update(Mockito.any(), createDataCaptor.capture(), Mockito.any()))
                    .thenReturn(new UpdateResult(entity, entity));
            datamodelApi.updatePartial(APPLICATION, EntityRequest.forEntity(INVOICE.getName(), entityId),
                    MapRequestInputData.fromMap(Map.of(
                    "customer", NullDataEntry.INSTANCE // Relation is set to null; but updates do not affect relations
                    )), PermissionPredicate.allowAll());

            assertThat(createDataCaptor.getValue().getId()).isEqualTo(entityId);
            assertThat(createDataCaptor.getValue().getName()).isEqualTo(INVOICE.getName());
            assertThat(createDataCaptor.getValue().getAttributes()).isEmpty();

            Mockito.verifyNoInteractions(contentStore);
        }

        @Test
        void contentAttributes_succeeds() throws InvalidPropertyDataException {
            var createDataCaptor = ArgumentCaptor.forClass(EntityData.class);
            var entityId = EntityId.of(UUID.randomUUID());
            var entity = EntityData.builder()
                    .name(INVOICE.getName())
                    .id(entityId)
                    .build();

            setupEntityQueryWithContent("content.bin");

            Mockito.when(queryEngine.update(Mockito.any(), createDataCaptor.capture(), Mockito.any()))
                    .thenReturn(new UpdateResult(entity, entity));
            datamodelApi.updatePartial(APPLICATION, EntityRequest.forEntity(INVOICE.getName(), entityId),
                    MapRequestInputData.fromMap(Map.of(
                            "content", Map.of(
                                    "filename", "file-123.pdf",
                                    "mimetype", MissingDataEntry.INSTANCE,
                                    "id", "will-be-ignored",
                                    "length", 0xbad
                            )
                    )),
                    PermissionPredicate.allowAll()
            );

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

            Mockito.verifyNoInteractions(contentStore);
        }

        @Test
        void contentAttributes_without_content_fails() {
            var entityId = EntityId.of(UUID.randomUUID());
            setupEntityQuery();
            assertThatThrownBy(() -> {
                datamodelApi.update(APPLICATION, EntityRequest.forEntity(INVOICE.getName(), entityId),
                        MapRequestInputData.fromMap(Map.of(
                        "number", "invoice-1",
                        "amount", 1.50,
                        "confidentiality", "public",
                        "content", Map.of(
                                "filename", "file-123.pdf",
                                "mimetype", "application/pdf",
                                "id", "will-be-ignored",
                                "length", 0xbad
                        )
                        )),
                        PermissionPredicate.allowAll()
                );
            }).isInstanceOfSatisfying(InvalidPropertyDataException.class, exception -> {
                assertThat(exception.allExceptions())
                        .anySatisfy(ex -> {
                            assertThat(ex.getCause()).isInstanceOf(ContentMissingInvalidDataException.class);
                            assertThat(ex.getPath().toList()).isEqualTo(List.of("content"));
                        });
            });

            Mockito.verify(queryEngine).findById(Mockito.any(), Mockito.any(), Mockito.any());

            Mockito.verifyNoMoreInteractions(queryEngine, contentStore);

        }

        @Test
        void contentAttributes_nullMimetype_fails() {
            setupEntityQueryWithContent("content.bin");
            var entityId = EntityId.of(UUID.randomUUID());
            assertThatThrownBy(() -> {
                datamodelApi.updatePartial(APPLICATION, EntityRequest.forEntity(INVOICE.getName(), entityId),
                        MapRequestInputData.fromMap(Map.of(
                                "content", Map.of(
                                        "filename", "file-123.pdf",
                                        "mimetype", NullDataEntry.INSTANCE
                                )
                        )),
                        PermissionPredicate.allowAll()
                );
            }).isInstanceOfSatisfying(InvalidPropertyDataException.class, exception -> {
                assertThat(exception.allExceptions())
                        .anySatisfy(ex -> {
                            assertThat(ex.getCause()).isInstanceOf(RequiredConstraintViolationInvalidDataException.class);
                            assertThat(ex.getPath().toList()).isEqualTo(List.of("content", "mimetype"));
                        });
            });
        }

        @Test
        void contentAttributes_missingMimetype_succeeds() throws InvalidPropertyDataException {
            setupEntityQueryWithContent("content.bin");
            var entityId = EntityId.of(UUID.randomUUID());
            var createDataCaptor = ArgumentCaptor.forClass(EntityData.class);
            var entity = EntityData.builder()
                    .name(INVOICE.getName())
                    .id(entityId)
                    .build();
            Mockito.when(queryEngine.update(Mockito.any(), createDataCaptor.capture(), Mockito.any()))
                    .thenReturn(new UpdateResult(entity, entity));
            datamodelApi.updatePartial(APPLICATION, EntityRequest.forEntity(INVOICE.getName(), entityId),
                    MapRequestInputData.fromMap(Map.of(
                            "content", Map.of(
                                    "filename", "test132.pdf",
                                    "mimetype", MissingDataEntry.INSTANCE
                            )
                    )),
                    PermissionPredicate.allowAll()
            );

            assertThat(createDataCaptor.getValue().getId()).isEqualTo(entityId);
            assertThat(createDataCaptor.getValue().getName()).isEqualTo(INVOICE.getName());
            assertThat(createDataCaptor.getValue().getAttributes()).containsExactlyInAnyOrder(
                    CompositeAttributeData.builder()
                            .name(INVOICE_CONTENT.getName())
                            // Content ID and size are kept/not overwritten, so they are not present here
                            // Mimetype is not overwritten, so it's also not present here
                            .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getFilename().getName(),
                                    "test132.pdf"))
                            .build()
            );
        }

        @Test
        void contentAttributes_nullFilename_succeeds() throws InvalidPropertyDataException {
            setupEntityQueryWithContent("content.bin");
            var entityId = EntityId.of(UUID.randomUUID());
            var createDataCaptor = ArgumentCaptor.forClass(EntityData.class);
            var entity = EntityData.builder()
                    .name(INVOICE.getName())
                    .id(entityId)
                    .build();
            Mockito.when(queryEngine.update(Mockito.any(), createDataCaptor.capture(), Mockito.any()))
                    .thenReturn(new UpdateResult(entity, entity));
            datamodelApi.updatePartial(APPLICATION, EntityRequest.forEntity(INVOICE.getName(), entityId),
                    MapRequestInputData.fromMap(Map.of(
                            "content", Map.of(
                                    "filename", NullDataEntry.INSTANCE,
                                    "mimetype", "application/pdf"
                            )
                    )),
                    PermissionPredicate.allowAll()
            );

            assertThat(createDataCaptor.getValue().getId()).isEqualTo(entityId);
            assertThat(createDataCaptor.getValue().getName()).isEqualTo(INVOICE.getName());
            assertThat(createDataCaptor.getValue().getAttributes()).containsExactlyInAnyOrder(
                    CompositeAttributeData.builder()
                            .name(INVOICE_CONTENT.getName())
                            // Content ID and size are kept/not overwritten, so they are not present here
                            .attribute(
                                    new SimpleAttributeData<>(INVOICE_CONTENT.getFilename().getName(), null))
                            .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getMimetype().getName(),
                                    "application/pdf"))
                            .build()
            );
        }

        @Test
        void contentAttributes_missingFilename_succeeds() throws InvalidPropertyDataException {
            setupEntityQueryWithContent("content.bin");
            var entityId = EntityId.of(UUID.randomUUID());
            var createDataCaptor = ArgumentCaptor.forClass(EntityData.class);
            var entity = EntityData.builder()
                    .name(INVOICE.getName())
                    .id(entityId)
                    .build();
            Mockito.when(queryEngine.update(Mockito.any(), createDataCaptor.capture(), Mockito.any()))
                    .thenReturn(new UpdateResult(entity, entity));
            datamodelApi.updatePartial(APPLICATION, EntityRequest.forEntity(INVOICE.getName(), entityId),
                    MapRequestInputData.fromMap(Map.of(
                            "content", Map.of(
                                    "filename", MissingDataEntry.INSTANCE,
                                    "mimetype", "application/pdf"
                            )
                    )),
                    PermissionPredicate.allowAll()
            );

            assertThat(createDataCaptor.getValue().getId()).isEqualTo(entityId);
            assertThat(createDataCaptor.getValue().getName()).isEqualTo(INVOICE.getName());
            assertThat(createDataCaptor.getValue().getAttributes()).containsExactlyInAnyOrder(
                    CompositeAttributeData.builder()
                            .name(INVOICE_CONTENT.getName())
                            // Content ID and size are kept/not overwritten, so they are not present here
                            // Filename is not overwritten, so it's also not present here
                            .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getMimetype().getName(),
                                    "application/pdf"))
                            .build()
            );
        }

        @Test
        void contentFile_succeeds() throws InvalidPropertyDataException, UnwritableContentException {
            setupEntityQuery();

            var createDataCaptor = ArgumentCaptor.forClass(EntityData.class);
            var entityId = EntityId.of(UUID.randomUUID());
            var fileId = "my-file-123.bin";
            var entity = EntityData.builder()
                    .name(INVOICE.getName())
                    .id(entityId)
                    .build();
            Mockito.when(queryEngine.update(Mockito.any(), createDataCaptor.capture(), Mockito.any()))
                    .thenReturn(new UpdateResult(entity, entity));
            Mockito.when(contentStore.createNewWriter()).thenAnswer(contentWriterFor(fileId, 150));
            datamodelApi.updatePartial(APPLICATION, EntityRequest.forEntity(INVOICE.getName(), entityId),
                    MapRequestInputData.fromMap(Map.of(
                    "content", new FileDataEntry("my-file.pdf", "application/pdf", InputStream::nullInputStream)
                    )), PermissionPredicate.allowAll());

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
                            .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getLength().getName(), 150L))
                            .build()
            );
        }
    }

    @Nested
    class FindAllEntities {

        private void mockCount() {
            // Count fails if not mocked, Mockito doesn't know how to handle ItemCount as return type
            Mockito.doReturn(ItemCount.exact(1_000_000))
                    .when(queryEngine).count(any(), any(), any());
        }

        @Test
        void findAllWithPaging() {
            ArgumentCaptor<QueryPageData> paginationArg = ArgumentCaptor.forClass(QueryPageData.class);
            Mockito.when(queryEngine.findAll(any(), any(), any(), any(), paginationArg.capture()))
                    .thenAnswer(invocation -> fakeFindAll(paginationArg.getValue()));

            mockCount();

            // cursor `null` -> first page
            var firstPage = datamodelApi.findAll(APPLICATION, INVOICE, Map.of(), new EncodedCursorPagination(null, 20, SortData.unsorted()), PermissionPredicate.allowAll());
            assertEquals(100.0, getAmount(firstPage.getContent().getFirst()));
            assertEquals(2000.0, getAmount(firstPage.getContent().getLast()));

            assertNotNull(firstPage.next().orElse(null));
            assertNull(firstPage.previous().orElse(null));

            // get the cursor for the next page from the result of the first page
            EncodedCursorPagination nextPageRequest = (EncodedCursorPagination) firstPage.getControls().next().orElseThrow();

            var secondPage = datamodelApi.findAll(APPLICATION, INVOICE, Map.of(), nextPageRequest, PermissionPredicate.allowAll());
            assertEquals(2100.0, getAmount(secondPage.getContent().getFirst()));
            assertEquals(4000.0, getAmount(secondPage.getContent().getLast()));

            assertNotNull(secondPage.next().orElse(null));
            assertNotNull(secondPage.previous().orElse(null));

            nextPageRequest = (EncodedCursorPagination) secondPage.next().orElseThrow();

            var thirdPage = datamodelApi.findAll(APPLICATION, INVOICE, Map.of(), nextPageRequest, PermissionPredicate.allowAll());
            assertEquals(4100.0, getAmount(thirdPage.getContent().getFirst()));
            assertEquals(6000.0, getAmount(thirdPage.getContent().getLast()));
        }

        @Test
        void findAllWithPagingAndLimits() {
            ArgumentCaptor<QueryPageData> paginationArg = ArgumentCaptor.forClass(QueryPageData.class);
            Mockito.when(queryEngine.findAll(any(), any(), any(), any(), paginationArg.capture()))
                    .thenAnswer(invocation -> fakeFindAll(paginationArg.getValue()));

            mockCount();

            // cursor `null` -> first page
            var firstPage = datamodelApi.findAll(APPLICATION, INVOICE, Map.of(), new EncodedCursorPagination(null, 50, SortData.unsorted()), PermissionPredicate.allowAll());
            assertEquals(100.0, getAmount(firstPage.getContent().getFirst()));
            assertEquals(5000.0, getAmount(firstPage.getContent().getLast()));

            assertNotNull(firstPage.next().orElse(null));
            assertNull(firstPage.previous().orElse(null));

            // get the cursor for the next page from the result of the first page
            EncodedCursorPagination nextPageRequest = (EncodedCursorPagination) firstPage.getControls().next().orElseThrow();

            var secondPage = datamodelApi.findAll(APPLICATION, INVOICE, Map.of(), nextPageRequest, PermissionPredicate.allowAll());
            assertEquals(5_100.0, getAmount(secondPage.getContent().getFirst()));
            assertEquals(10_000.0, getAmount(secondPage.getContent().getLast()));

            assertNotNull(secondPage.next().orElse(null));
            assertNotNull(secondPage.previous().orElse(null));

            nextPageRequest = (EncodedCursorPagination) secondPage.next().orElseThrow();

            var thirdPage = datamodelApi.findAll(APPLICATION, INVOICE, Map.of(), nextPageRequest, PermissionPredicate.allowAll());
            assertEquals(10_100.0, getAmount(thirdPage.getContent().getFirst()));
            assertEquals(15_000.0, getAmount(thirdPage.getContent().getLast()));
        }

        @Test
        void findAllWithPagingAndFiltering() {
            ArgumentCaptor<QueryPageData> paginationArg = ArgumentCaptor.forClass(QueryPageData.class);
            var filter = LogicalOperation.conjunction(
                    StringComparison.areEqual(SymbolicReference.parse("entity.confidentiality"), Scalar.of("public")),
                    Scalar.of(true)
            );
            Mockito.when(queryEngine.findAll(any(), any(), eq(filter), any(), paginationArg.capture()))
                    .thenAnswer(invocation -> fakeFindAll(paginationArg.getValue(),
                            data -> getConfidentiality(data).equals("public")
                    ));

            mockCount();

            // cursor `null` -> first page
            var firstPage = datamodelApi.findAll(APPLICATION, INVOICE, Map.of("confidentiality", "public"), new EncodedCursorPagination(null, 20, SortData.unsorted()), PermissionPredicate.allowAll());
            assertEquals(100.0, getAmount(firstPage.getContent().getFirst()));
            assertEquals(3900.0, getAmount(firstPage.getContent().getLast()));

            // get the cursor for the next page from the result of the first page
            EncodedCursorPagination nextPageRequest = (EncodedCursorPagination) firstPage.getControls().next().orElseThrow();

            var secondPage = datamodelApi.findAll(APPLICATION, INVOICE, Map.of("confidentiality", "public"), nextPageRequest, PermissionPredicate.allowAll());
            assertEquals(4100.0, getAmount(secondPage.getContent().getFirst()));
            assertEquals(7900.0, getAmount(secondPage.getContent().getLast()));

            nextPageRequest = (EncodedCursorPagination) secondPage.getControls().next().orElseThrow();

            var thirdPage = datamodelApi.findAll(APPLICATION, INVOICE, Map.of("confidentiality", "public"), nextPageRequest, PermissionPredicate.allowAll());
            assertEquals(8100.0, getAmount(thirdPage.getContent().getFirst()));
            assertEquals(11900.0, getAmount(thirdPage.getContent().getLast()));
        }

        @Test
        void findAllWithPagingAndSorting() {
            ArgumentCaptor<QueryPageData> paginationArg = ArgumentCaptor.forClass(QueryPageData.class);
            SortData sort = new SortData(List.of(new FieldSort(Direction.DESC, SortableName.of("amount"))));
            Mockito.when(queryEngine.findAll(any(), any(), any(), eq(sort), paginationArg.capture()))
                    .thenAnswer(invocation -> fakeFindAll(paginationArg.getValue(), Direction.DESC));

            mockCount();

            // cursor `null` -> first page
            var firstPage = datamodelApi.findAll(APPLICATION, INVOICE, Map.of(), new EncodedCursorPagination(null, 20, sort), PermissionPredicate.allowAll());
            assertEquals(100_000_000.0, getAmount(firstPage.getContent().getFirst()));
            assertEquals(99_998_100.0, getAmount(firstPage.getContent().getLast()));

            // get the cursor for the next page from the result of the first page
            EncodedCursorPagination nextPageRequest = (EncodedCursorPagination) firstPage.getControls().next().orElseThrow();

            var secondPage = datamodelApi.findAll(APPLICATION, INVOICE, Map.of(), nextPageRequest, PermissionPredicate.allowAll());
            assertEquals(99_998_000.0, getAmount(secondPage.getContent().getFirst()));
            assertEquals(99_996_100.0, getAmount(secondPage.getContent().getLast()));

            nextPageRequest = (EncodedCursorPagination) secondPage.getControls().next().orElseThrow();

            var thirdPage = datamodelApi.findAll(APPLICATION, INVOICE, Map.of(), nextPageRequest, PermissionPredicate.allowAll());
            assertEquals(99_996_000.0, getAmount(thirdPage.getContent().getFirst()));
            assertEquals(99_994_100.0, getAmount(thirdPage.getContent().getLast()));
        }

        @Test
        void findAllWithPagingNavigation() {
            ArgumentCaptor<QueryPageData> paginationArg = ArgumentCaptor.forClass(QueryPageData.class);
            Mockito.when(queryEngine.findAll(any(), any(), any(), any(), paginationArg.capture()))
                    .thenAnswer(invocation -> fakeFindAll(paginationArg.getValue()));

            mockCount();

            // cursor `null` -> first page
            var startPage = datamodelApi.findAll(APPLICATION, INVOICE, Map.of(), new EncodedCursorPagination(null, 20, SortData.unsorted()), PermissionPredicate.allowAll());

            // Navigate to third page (next page is tested in other tests)
            var secondPage = datamodelApi.findAll(APPLICATION, INVOICE, Map.of(), (EncodedCursorPagination) startPage.next().orElseThrow(), PermissionPredicate.allowAll());
            var thirdPage = datamodelApi.findAll(APPLICATION, INVOICE, Map.of(), (EncodedCursorPagination) secondPage.next().orElseThrow(), PermissionPredicate.allowAll());

            // Verify that navigating to current page remains the same
            var currentPage = datamodelApi.findAll(APPLICATION, INVOICE, Map.of(), (EncodedCursorPagination) thirdPage.current(), PermissionPredicate.allowAll());
            assertEquals(getAmount(thirdPage.getContent().getFirst()), getAmount(currentPage.getContent().getFirst()));
            assertEquals(getAmount(thirdPage.getContent().getLast()), getAmount(currentPage.getContent().getLast()));

            // Verify that previous page is the same as second page
            var prevPage = datamodelApi.findAll(APPLICATION, INVOICE, Map.of(), (EncodedCursorPagination) thirdPage.previous().orElseThrow(), PermissionPredicate.allowAll());
            assertEquals(getAmount(secondPage.getContent().getFirst()), getAmount(prevPage.getContent().getFirst()));
            assertEquals(getAmount(secondPage.getContent().getLast()), getAmount(prevPage.getContent().getLast()));

            // Verify that first page is the same as starting page
            var firstPage = datamodelApi.findAll(APPLICATION, INVOICE, Map.of(), (EncodedCursorPagination) thirdPage.first(), PermissionPredicate.allowAll());
            assertEquals(getAmount(startPage.getContent().getFirst()), getAmount(firstPage.getContent().getFirst()));
            assertEquals(getAmount(startPage.getContent().getLast()), getAmount(firstPage.getContent().getLast()));
        }

        static Stream<Arguments> findAllWithCounts() {
            return Stream.of(
                    Arguments.argumentSet("exact, no results", 0, 0, -1, ItemCount.exact(0)),
                    Arguments.argumentSet("exact, only page", 0, 5, -1, ItemCount.exact(5)),
                    Arguments.argumentSet("exact, first page", 0, 25, -1, ItemCount.exact(25)),
                    Arguments.argumentSet("exact, second page", 1, 25, -1, ItemCount.exact(25)),
                    Arguments.argumentSet("exact, last page", 2, 25, -1, ItemCount.exact(25)),
                    Arguments.argumentSet("exact, empty page", 3, 25, -1, ItemCount.exact(25)),
                    Arguments.argumentSet("under-estimate, only page", 0, 5, 2, ItemCount.exact(5)),
                    Arguments.argumentSet("under-estimate, first page", 0, 25, 12, ItemCount.estimated(12)),
                    Arguments.argumentSet("under-estimate, second page", 1, 25, 12, ItemCount.estimated(21)),
                    Arguments.argumentSet("under-estimate, last page", 2, 25, 12, ItemCount.exact(25)),
                    Arguments.argumentSet("under-estimate, empty page", 3, 25, 12, ItemCount.estimated(12)),
                    Arguments.argumentSet("over-estimate, no results", 0, 0, 8, ItemCount.exact(0)),
                    Arguments.argumentSet("over-estimate, only page", 0, 5, 8, ItemCount.exact(5)),
                    Arguments.argumentSet("over-estimate, first page", 0, 25, 40, ItemCount.estimated(40)),
                    Arguments.argumentSet("over-estimate, second page", 1, 25, 40, ItemCount.estimated(40)),
                    Arguments.argumentSet("over-estimate, last page", 2, 25, 40, ItemCount.exact(25)),
                    Arguments.argumentSet("over-estimate, empty page", 3, 25, 40, ItemCount.estimated(30)),
                    Arguments.argumentSet("unknown, only page", 0, 5, 0, ItemCount.exact(5)),
                    Arguments.argumentSet("unknown, first page", 0, 25, 0, ItemCount.estimated(11)),
                    Arguments.argumentSet("unknown, second page", 1, 25, 0, ItemCount.estimated(21)),
                    Arguments.argumentSet("unknown, last page", 2, 25, 0, ItemCount.exact(25)),
                    Arguments.argumentSet("unknown, empty page", 3, 25, 0, ItemCount.estimated(30))
            );
        }

        @ParameterizedTest
        @MethodSource
        void findAllWithCounts(int page, long exact, long estimated, ItemCount expected) {
            var size = 10;
            var isExact = estimated < 0;
            var isEstimated = estimated > 0; // when 0, it is unknown.

            // Mockito requires to only stub methods that effectively get called
            // In this case we only need to stub count method(s) when we are not on the last page
            var stubNeeded = (long) page * size >= exact || (page + 1L) * size < exact;

            if (exact == 0 && page == 0) {
                // no results => no count needed
                stubNeeded = false;
            }

            // Setup mock for encoding/decoding fake cursors
            Mockito.doReturn(new PageBasedPagination(size, page))
                    .when(codec)
                    .decodeCursor(any(), any(), any());
            ArgumentCaptor<Pagination> paginationArg = ArgumentCaptor.forClass(Pagination.class);

            Mockito.doAnswer(invocation -> {
                var pagination = (PageBasedPagination) paginationArg.getValue();
                var cursor = fakeCursor(pagination.getPage());
                return new CursorContext(cursor, size, SortData.unsorted());
            }).when(codec).encodeCursor(paginationArg.capture(), any(), any(), any());

            // mock queryEngine
            ArgumentCaptor<QueryPageData> pageArg = ArgumentCaptor.forClass(QueryPageData.class);
            Mockito.when(queryEngine.findAll(any(), any(), any(), any(), pageArg.capture()))
                    .thenAnswer(invocation -> fakeFindAll(pageArg.getValue(), exact));

            if (stubNeeded) {
                ItemCount itemCount = isExact ? ItemCount.exact(exact) :
                        (isEstimated ? ItemCount.estimated(estimated) : ItemCount.unknown());
                Mockito.doReturn(itemCount)
                        .when(queryEngine).count(any(), any(), any());
            }

            var result = datamodelApi.findAll(APPLICATION, INVOICE, Map.of(), new EncodedCursorPagination(fakeCursor(page), size, SortData.unsorted()), PermissionPredicate.allowAll());
            assertEquals(expected, result.getTotalItemCount());

            // assert count was not called when stubNeeded is false
            Mockito.verifyNoMoreInteractions(queryEngine);
        }

        private double getAmount(EntityData entity) {
            var data = entity.getAttributeByName(INVOICE_AMOUNT.getName()).orElseThrow();
            return ((SimpleAttributeData<Double>) data).getValue();
        }
        private String getConfidentiality(EntityData entity) {
            var data = entity.getAttributeByName(INVOICE_CONFIDENTIALITY.getName()).orElseThrow();
            return ((SimpleAttributeData<String>) data).getValue();
        }

        private String fakeCursor(int page) {
            return page <= 0 ? null : Integer.toString(page);
        }

        private SliceData fakeFindAll(QueryPageData page) {
            return fakeFindAll(page, data -> true, 1_000_000, false);
        }

        private SliceData fakeFindAll(QueryPageData page, Predicate<EntityData> filter) {
            return fakeFindAll(page, filter, 1_000_000, false);
        }

        private SliceData fakeFindAll(QueryPageData page, Direction direction) {
            return fakeFindAll(page, data -> true, 1_000_000, direction == Direction.DESC);
        }

        private SliceData fakeFindAll(QueryPageData page, long count) {
            return fakeFindAll(page, data -> true, (int) count, false);
        }

        private SliceData fakeFindAll(QueryPageData page, Predicate<EntityData> filter, int count, boolean descending) {
            var pageData = (OffsetData) page;
            var offset = pageData.getOffset();
            var limit = pageData.getLimit();

            List<EntityData> entities = Stream
                    // stream of 1, 2, 3, ..., count
                    .iterate(1, i -> i <= count, i -> i+1)
                    // count down if descending
                    .map(descending ? i -> count + 1 - i : Function.identity())
                    // transform to {foo, 100}, {bar, 200}, {foo, 300}, ...
                    .map(FindAllEntities::fakeInvoice)
                    // apply filter (should match what the ThunkExpression would do)
                    .filter(filter)
                    // do paging equivalent
                    .skip(offset)
                    .limit(limit)
                    .toList();

            return SliceData.builder()
                    .entities(entities)
                    .build();
        }

        private static EntityData fakeInvoice(int i) {
            return EntityData.builder()
                    .name(INVOICE.getName())
                    .id(fakeId(i))
                    .attribute(SimpleAttributeData.<String>builder()
                            .name(INVOICE_NUMBER.getName())
                            .value("invoice_" + i)
                            .build())
                    .attribute(SimpleAttributeData.<Double>builder()
                            .name(INVOICE_AMOUNT.getName())
                            .value(i * 100.0)
                            .build())
                    .attribute(SimpleAttributeData.<String>builder()
                            .name(INVOICE_CONFIDENTIALITY.getName())
                            .value(i % 2 == 1 ? "public" : "confidential")
                            .build())
                    .build();
        }

        private static EntityId fakeId(int i) {
            var hex = Integer.toHexString(i);
            var val = "00000000-0000-0000-0000-0000" + "0".repeat(8 - hex.length()) + hex;
            return EntityId.of(UUID.fromString(val));
        }
    }

    @Nested
    class DeleteEntity {
        @Test
        void deleteSuccess() {
            EntityId id = EntityId.of(UUID.randomUUID());
            EntityName invoice = EntityName.of("invoice");
            EntityData data = EntityData.builder().name(invoice).id(id).attributes(List.of()).build();

            ArgumentCaptor<EntityRequest> deleteArg = ArgumentCaptor.forClass(EntityRequest.class);
            Mockito.when(queryEngine.delete(Mockito.any(), deleteArg.capture(), Mockito.any()))
                    .thenReturn(Optional.of(data));

            datamodelApi.deleteEntity(APPLICATION, EntityRequest.forEntity(invoice, id), PermissionPredicate.allowAll());
            assertEquals(invoice, deleteArg.getValue().getEntityName());
            assertEquals(id, deleteArg.getValue().getEntityId());
        }

        @Test
        void deleteNonExistent() {
            EntityId id = EntityId.of(UUID.randomUUID());
            EntityName invoice = EntityName.of("invoice");

            Mockito.when(queryEngine.delete(Mockito.any(), Mockito.any(), Mockito.any()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    datamodelApi.deleteEntity(APPLICATION, EntityRequest.forEntity(invoice, id),
                            PermissionPredicate.allowAll())
            ).isInstanceOf(EntityIdNotFoundException.class);

        }
    }
}
