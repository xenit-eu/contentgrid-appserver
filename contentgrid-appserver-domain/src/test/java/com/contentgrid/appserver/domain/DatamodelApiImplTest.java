package com.contentgrid.appserver.domain;

import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.MultivalueAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.attributes.flags.ReadOnlyFlag;
import com.contentgrid.appserver.application.model.links.EntityLink;
import com.contentgrid.appserver.application.model.links.LinkIdentity.NamedLink;
import com.contentgrid.appserver.application.model.links.LinkIdentity.UnnamedLink;
import com.contentgrid.appserver.application.model.links.UriTemplateDefinition;
import com.contentgrid.appserver.application.model.links.UriTemplateDefinition.AutomationUriTemplateDefinition;
import com.contentgrid.appserver.application.model.links.UriTemplateDefinition.EntityLinkSubstitutionVariables;
import com.contentgrid.appserver.application.model.links.UriTemplateDefinition.SimpleUriTemplateDefinition;
import com.contentgrid.appserver.application.model.propertypath.SimpleAttributePath;
import com.contentgrid.appserver.application.model.propertypath.SimpleRelationPath;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.SortableName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.contentstore.api.ContentAccessor;
import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.contentstore.api.UnwritableContentException;
import com.contentgrid.appserver.domain.authorization.AuthorizationContext;
import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.DecimalDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.FileDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.FileDataEntry.InputStreamSupplier;
import com.contentgrid.appserver.domain.data.DataEntry.ListDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MissingDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.NullDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.RelationDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.StringDataEntry;
import com.contentgrid.appserver.domain.data.EntityInstance;
import com.contentgrid.appserver.domain.data.EntityLinkData;
import com.contentgrid.appserver.domain.data.InvalidDataFormatException;
import com.contentgrid.appserver.domain.data.InvalidDataTypeException;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.domain.data.MapRequestInputData;
import com.contentgrid.appserver.domain.data.validation.AllowedValuesConstraintViolationInvalidDataException;
import com.contentgrid.appserver.domain.data.validation.ContentMissingInvalidDataException;
import com.contentgrid.appserver.domain.data.validation.DuplicateElementInvalidDataException;
import com.contentgrid.appserver.domain.data.validation.RequiredConstraintViolationInvalidDataException;
import com.contentgrid.appserver.domain.data.validation.TextSetValidator;
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
import com.contentgrid.appserver.domain.values.User;
import com.contentgrid.appserver.query.engine.api.QueryEngine;
import com.contentgrid.appserver.query.engine.api.UpdateResult;
import com.contentgrid.appserver.query.engine.api.data.AttributeData;
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
import com.contentgrid.hateoas.uritemplate.ParameterizedUriTemplateParser;
import com.contentgrid.thunx.predicates.model.LogicalOperation;
import com.contentgrid.thunx.predicates.model.Scalar;
import com.contentgrid.thunx.predicates.model.SymbolicReference;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
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

    private static final Map<String, List<String>> PARAMS = Map.of();

    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    private QueryEngine queryEngine;
    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    private ContentStore contentStore;
    @Mock
    private DomainEventDispatcher domainEventDispatcher;
    @Spy
    private CursorCodec codec = new RequestIntegrityCheckCursorCodec(new SimplePageBasedCursorCodec());

    private DatamodelApi datamodelApi;

    private final LinkUriProvider linkUriProvider = new LinkUriProvider() {
        @Override
        public String createEntityLink(EntityIdentity entityIdentity) {
            return "http://localhost/%s/%s".formatted(
                    entityIdentity.getEntityName().getValue(),
                    entityIdentity.getEntityId().getValue()
            );
        }

        @Override
        public String createAttributeLink(EntityIdentity entityIdentity, AttributeName attributeName) {
            return createEntityLink(entityIdentity) + "/" + attributeName.getValue();
        }

        @Override
        public String createRelationLink(EntityIdentity entityIdentity, RelationName relationName) {
            return createEntityLink(entityIdentity) + "/" + relationName.getValue();
        }
    };

    private final ConfigurationProperties configurationProperties = new ConfigurationProperties() {
        @Override
        public String getApplicationId() {
            return "my-application-id";
        }

        @Override
        public Optional<URI> getAutomationSystemBaseUrl(String automationSystemId, String basePathName) {
            if (automationSystemId.equals("my-automation") && basePathName.equals("api")) {
                return Optional.of(URI.create("https://automation.example/my-automation"));
            }
            return Optional.empty();
        }
    };

    private static final Clock clock = Clock.fixed(Instant.ofEpochSecond(440991035), ZoneOffset.UTC);

    private static CompositeAttributeData getAuditMetadataData(boolean create) {
        var builder = CompositeAttributeData.builder()
                .name(INVOICE_AUDIT_METADATA.getName());
        if (create) {
            builder
                    .attribute(new SimpleAttributeData<>(AttributeName.of("created_date"), Instant.now(clock)))
                    .attribute(CompositeAttributeData.builder().name(AttributeName.of("created_by"))
                            .attribute(new SimpleAttributeData<String>(AttributeName.of("id"), null))
                            .attribute(new SimpleAttributeData<String>(AttributeName.of("namespace"), null))
                            .attribute(new SimpleAttributeData<String>(AttributeName.of("name"), null))
                            .build());
        }
        return builder
                .attribute(new SimpleAttributeData<>(AttributeName.of("last_modified_date"), Instant.now(clock)))
                .attribute(CompositeAttributeData.builder().name(AttributeName.of("last_modified_by"))
                        .attribute(new SimpleAttributeData<String>(AttributeName.of("id"), null))
                        .attribute(new SimpleAttributeData<String>(AttributeName.of("namespace"), null))
                        .attribute(new SimpleAttributeData<String>(AttributeName.of("name"), null))
                        .build())
                .build();
    }

    @BeforeEach
    void setup() {
        datamodelApi = new DatamodelApiImpl(
                queryEngine,
                application -> contentStore,
                domainEventDispatcher,
                application -> linkUriProvider,
                application -> configurationProperties,
                codec,
                clock
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
    class TextSetAttribute {

        private static final MultivalueAttribute DOCUMENT_TAGS = MultivalueAttribute.builder()
                .name(AttributeName.of("tags"))
                .column(ColumnName.of("tags"))
                .itemType(Type.TEXT)
                .build();

        private static final MultivalueAttribute DOCUMENT_LABELS = MultivalueAttribute.builder()
                .name(AttributeName.of("labels"))
                .column(ColumnName.of("labels"))
                .itemType(Type.TEXT)
                .constraint(Constraint.allowedValues(List.of("hr", "it", "finance")))
                .build();

        private static final Entity DOCUMENT = Entity.builder()
                .name(EntityName.of("document"))
                .table(TableName.of("document"))
                .pathSegment(PathSegmentName.of("documents"))
                .linkName(LinkName.of("documents"))
                .attribute(DOCUMENT_TAGS)
                .attribute(DOCUMENT_LABELS)
                .build();

        private static final Application TEXT_SET_APPLICATION = Application.builder()
                .name(ApplicationName.of("text-set-application"))
                .entity(DOCUMENT)
                .build();

        private EntityCreateData createDocument(Map<String, Object> data) throws InvalidPropertyDataException {
            var createDataCaptor = ArgumentCaptor.forClass(EntityCreateData.class);
            Mockito.when(queryEngine.create(Mockito.any(), createDataCaptor.capture(), Mockito.any(), Mockito.any()))
                    .thenReturn(EntityData.builder().name(DOCUMENT.getName()).id(EntityId.of(UUID.randomUUID()))
                            .build());
            datamodelApi.create(TEXT_SET_APPLICATION, DOCUMENT.getName(), MapRequestInputData.fromMap(data),
                    AuthorizationContext.allowAll());
            return createDataCaptor.getValue();
        }

        private void expectCreateFailure(Map<String, Object> data, Class<? extends Exception> causeType) {
            assertThatThrownBy(() -> datamodelApi.create(TEXT_SET_APPLICATION, DOCUMENT.getName(),
                    MapRequestInputData.fromMap(data), AuthorizationContext.allowAll()))
                    .isInstanceOfSatisfying(InvalidPropertyDataException.class, exception ->
                            assertThat(exception.allExceptions())
                                    .isNotEmpty()
                                    .allSatisfy(ex -> assertThat(ex.getCause()).isInstanceOf(causeType)));
            Mockito.verifyNoInteractions(queryEngine, contentStore);
        }

        @Test
        void writeElements_succeeds() throws InvalidPropertyDataException {
            var createData = createDocument(Map.of(
                    "tags", List.of("urgent", "vip"),
                    "labels", List.of("hr", "it")
            ));
            assertThat(createData.getAttributes()).containsExactlyInAnyOrder(
                    new SimpleAttributeData<>(DOCUMENT_TAGS.getName(), List.of("urgent", "vip")),
                    new SimpleAttributeData<>(DOCUMENT_LABELS.getName(), List.of("hr", "it"))
            );
        }

        @Test
        void omittedValue_becomesEmptyList() throws InvalidPropertyDataException {
            var createData = createDocument(Map.of());
            assertThat(createData.getAttributes()).containsExactlyInAnyOrder(
                    new SimpleAttributeData<>(DOCUMENT_TAGS.getName(), List.of()),
                    new SimpleAttributeData<>(DOCUMENT_LABELS.getName(), List.of())
            );
        }

        @Test
        void nullValue_becomesEmptyList() throws InvalidPropertyDataException {
            var data = new HashMap<String, Object>();
            data.put("tags", null);
            var createData = createDocument(data);
            assertThat(createData.getAttributes()).contains(
                    new SimpleAttributeData<>(DOCUMENT_TAGS.getName(), List.of())
            );
        }

        @Test
        void duplicateElements_fails() {
            expectCreateFailure(Map.of("tags", List.of("urgent", "vip", "urgent")),
                    DuplicateElementInvalidDataException.class);
        }

        @Test
        void nfkcEquivalentElements_fails() {
            // The same value in composed (NFC) and decomposed (NFD) encoding is a duplicate
            expectCreateFailure(Map.of("tags", List.of("café", "café")),
                    DuplicateElementInvalidDataException.class);
        }

        @Test
        void tooManyElements_fails() {
            var overLimit = IntStream.rangeClosed(0, TextSetValidator.MAX_ELEMENTS)
                    .mapToObj("value-%d"::formatted)
                    .toList();
            expectCreateFailure(Map.of("tags", overLimit), InvalidDataFormatException.class);
        }

        @Test
        void scalarValue_fails() {
            assertThatThrownBy(() -> datamodelApi.create(TEXT_SET_APPLICATION, DOCUMENT.getName(),
                    MapRequestInputData.fromMap(Map.of("tags", "urgent")), AuthorizationContext.allowAll()))
                    .isInstanceOfSatisfying(InvalidPropertyDataException.class, exception ->
                            assertThat(exception.allExceptions()).singleElement().satisfies(ex -> {
                                var invalidType = (InvalidDataTypeException) ex.getCause();
                                assertThat(invalidType.getExpectedType().getTechnicalName()).isEqualTo("string_set");
                                assertThat(ex.getPath().toString()).isEqualTo("tags");
                            }));
            Mockito.verifyNoInteractions(queryEngine, contentStore);
        }

        @Test
        void nullElement_fails() {
            expectCreateFailure(Map.of("tags", Arrays.asList("urgent", null)),
                    InvalidDataTypeException.class);
        }

        @Test
        void nonStringElement_fails() {
            expectCreateFailure(Map.of("tags", List.of("urgent", 123)),
                    InvalidDataTypeException.class);
        }

        @Test
        void elementOutsideAllowedValues_fails() {
            expectCreateFailure(Map.of("labels", List.of("hr", "legal")),
                    AllowedValuesConstraintViolationInvalidDataException.class);
        }

        private EntityInstance findDocument(List<AttributeData> attributes) {
            Mockito.when(queryEngine.findById(Mockito.any(), Mockito.any(), Mockito.any())).then(args -> {
                var request = args.getArgument(1, EntityRequest.class);
                return Optional.of(new EntityData(
                        EntityIdentity.forEntity(request.getEntityName(), request.getEntityId()),
                        attributes
                ));
            });
            return datamodelApi.findById(TEXT_SET_APPLICATION,
                            EntityRequest.forEntity(DOCUMENT.getName(), EntityId.of(UUID.randomUUID())),
                            AuthorizationContext.allowAll())
                    .orElseThrow();
        }

        @Test
        void readValues_returnsJsonArrayEntries() {
            var result = findDocument(List.of(
                    new SimpleAttributeData<>(DOCUMENT_TAGS.getName(), List.of("urgent", "ethias")),
                    new SimpleAttributeData<>(DOCUMENT_LABELS.getName(), List.of())
            ));
            assertThat(result.getData().get("tags")).isEqualTo(new ListDataEntry(List.of(
                    new StringDataEntry("urgent"), new StringDataEntry("ethias"))));
            assertThat(result.getData().get("labels")).isEqualTo(new ListDataEntry(List.of()));
        }

        @Test
        void readNullOrAbsentValue_returnsEmptyList() {
            // Defensive: the column is NOT NULL, but data predating the type must still read as an array
            var result = findDocument(List.of(
                    new SimpleAttributeData<>(DOCUMENT_TAGS.getName(), null)
                    // no data for labels at all
            ));
            assertThat(result.getData().get("tags")).isEqualTo(new ListDataEntry(List.of()));
            assertThat(result.getData().get("labels")).isEqualTo(new ListDataEntry(List.of()));
        }
    }

    @Nested
    class CreateEntity {
        @Test
        void allSimpleProperties_succeeds() throws InvalidPropertyDataException {
            var createDataCaptor = ArgumentCaptor.forClass(EntityCreateData.class);
            var entityId = EntityId.of(UUID.randomUUID());
            var personId = EntityId.of(UUID.randomUUID());
            Mockito.when(queryEngine.create(Mockito.any(), createDataCaptor.capture(), Mockito.any(), Mockito.any()))
                    .thenReturn(EntityData.builder().name(INVOICE.getName()).id(entityId).build());
            var result = datamodelApi.create(APPLICATION, INVOICE.getName(), MapRequestInputData.fromMap(Map.of(
                            "number", "1",
                            "amount", 1.50,
                            "received", LocalDate.now(clock),
                            "pay_before", LocalDate.now(clock).plusDays(30),
                            "pay_timestamp", Instant.now(clock).plus(7, ChronoUnit.DAYS),
                            "is_paid", false,
                            "confidentiality", "public",
                            "customer", new RelationDataEntry(PERSON.getName(), personId)
                    )),
                    AuthorizationContext.allowAll()
            );

            assertThat(result.getIdentity().getEntityId()).isEqualTo(entityId);

            assertThat(createDataCaptor.getValue()).satisfies(createData -> {
                assertThat(createData.getEntityName()).isEqualTo(INVOICE.getName());
                assertThat(createData.getAttributes())
                        .containsExactlyInAnyOrder(
                        new SimpleAttributeData<>(INVOICE_NUMBER.getName(), "1"),
                        new SimpleAttributeData<>(INVOICE_AMOUNT.getName(), BigDecimal.valueOf(1.50)),
                        new SimpleAttributeData<>(INVOICE_RECEIVED.getName(), LocalDate.now(clock)),
                        new SimpleAttributeData<>(INVOICE_PAY_BEFORE.getName(), LocalDate.now(clock).plusDays(30)),
                        new SimpleAttributeData<>(INVOICE_PAY_TIMESTAMP.getName(), Instant.now(clock).plus(7, ChronoUnit.DAYS)),
                        new SimpleAttributeData<>(INVOICE_IS_PAID.getName(), false),
                        new SimpleAttributeData<>(INVOICE_CONFIDENTIALITY.getName(), "public"),
                        CompositeAttributeData.builder()
                                .name(INVOICE_CONTENT.getName())
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getId().getName(), null))
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getFilename().getName(), null))
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getMimetype().getName(), null))
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getLength().getName(), null))
                                .build(),
                                getAuditMetadataData(true)
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
                        "received", LocalDate.now(clock),
                        "confidentiality", "public"
                )), AuthorizationContext.allowAll());
            }).isInstanceOfSatisfying(InvalidPropertyDataException.class, exception -> {
                assertThat(exception.allExceptions())
                        .allSatisfy(ex -> {
                            assertThat(ex.getCause()).isInstanceOf(RequiredConstraintViolationInvalidDataException.class);
                        })
                        .extracting(e -> e.getPath().toString())
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
                )), AuthorizationContext.allowAll());
            }).isInstanceOfSatisfying(InvalidPropertyDataException.class, exception -> {
                assertThat(exception.allExceptions())
                        .allSatisfy(ex -> {
                            assertThat(ex.getCause()).isInstanceOf(InvalidDataTypeException.class);
                        })
                        .extracting(e -> e.getPath().toString())
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
        void notAllowedValue_fails() {
            var personId = EntityId.of(UUID.randomUUID());

            assertThatThrownBy(() -> {
                datamodelApi.create(APPLICATION, INVOICE.getName(), MapRequestInputData.fromMap(Map.of(
                        "number", "1",
                        "amount", 1.50,
                        "received", LocalDate.now(clock),
                        "pay_before", LocalDate.now(clock).plusDays(30),
                        "pay_timestamp", Instant.now(clock).plus(7, ChronoUnit.DAYS),
                        "is_paid", false,
                        "confidentiality", "xyz123",
                        "customer", new RelationDataEntry(PERSON.getName(), personId)
                )), AuthorizationContext.allowAll());
            }).isInstanceOfSatisfying(InvalidPropertyDataException.class, exception -> {
                assertThat(exception.allExceptions())
                        .allSatisfy(ex -> {
                            assertThat(ex.getCause()).isInstanceOf(AllowedValuesConstraintViolationInvalidDataException.class);
                        })
                        .extracting(e -> e.getPath().toString())
                        .containsExactlyInAnyOrder(
                                "confidentiality"
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
            Mockito.when(queryEngine.create(Mockito.any(), createDataCaptor.capture(), Mockito.any(), Mockito.any()))
                    .thenReturn(EntityData.builder().name(INVOICE.getName()).id(entityId).build());

            var result = datamodelApi.create(APPLICATION, INVOICE.getName(), MapRequestInputData.fromMap(Map.of(
                            "number", "1",
                            "amount", 1.50,
                            "confidentiality", "public",
                            "customer", new RelationDataEntry(PERSON.getName(), personId),
                            "products", productIds.stream()
                                    .map(pid -> new RelationDataEntry(PRODUCT.getName(), pid))
                                    .toList()
                    )),
                    AuthorizationContext.allowAll()
            );

            assertThat(result.getIdentity().getEntityId()).isEqualTo(entityId);

            assertThat(createDataCaptor.getValue()).satisfies(createData -> {
                assertThat(createData.getEntityName()).isEqualTo(INVOICE.getName());
                assertThat(createData.getAttributes())
                        .containsExactlyInAnyOrder(
                        new SimpleAttributeData<>(INVOICE_NUMBER.getName(), "1"),
                        new SimpleAttributeData<>(INVOICE_AMOUNT.getName(), BigDecimal.valueOf(1.50)),
                        new SimpleAttributeData<>(INVOICE_CONFIDENTIALITY.getName(), "public"),
                        new SimpleAttributeData<>(INVOICE_RECEIVED.getName(), null),
                        new SimpleAttributeData<>(INVOICE_PAY_BEFORE.getName(), null),
                        new SimpleAttributeData<>(INVOICE_PAY_TIMESTAMP.getName(), null),
                        new SimpleAttributeData<>(INVOICE_IS_PAID.getName(), null),
                        CompositeAttributeData.builder()
                                .name(INVOICE_CONTENT.getName())
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getId().getName(), null))
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getFilename().getName(), null))
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getMimetype().getName(), null))
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getLength().getName(), null))
                                .build(),
                                getAuditMetadataData(true)
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
            Mockito.when(queryEngine.create(Mockito.any(), createDataCaptor.capture(), Mockito.any(), Mockito.any()))
                    .thenReturn(EntityData.builder().name(PERSON.getName()).id(entityId)
                            // The vat attribute is referenced by the owner of PERSON_VAT_LINK
                            .attribute(new SimpleAttributeData<>(PERSON_VAT.getName(), "XXXX"))
                            .build());
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
                    AuthorizationContext.allowAll()
            );

            assertThat(result.getIdentity().getEntityId()).isEqualTo(entityId);
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
            Mockito.when(queryEngine.create(Mockito.any(), createDataCaptor.capture(), Mockito.any(), Mockito.any()))
                    .thenReturn(EntityData.builder().name(PERSON.getName()).id(entityId)
                            // The vat attribute is referenced by the owner of PERSON_VAT_LINK
                            .attribute(new SimpleAttributeData<>(PERSON_VAT.getName(), "XXXX"))
                            .build());
            var result = datamodelApi.create(APPLICATION, PERSON.getName(), MapRequestInputData.fromMap(Map.of(
                    "name", "test",
                    "vat", "123456"
                    // person also has a uni-directional "friends" relation that we don't provide here.
                    // The inverse relation is unnamed, so it should also not be processed
            )), AuthorizationContext.allowAll());

            assertThat(result.getIdentity().getEntityId()).isEqualTo(entityId);

            assertThat(createDataCaptor.getValue()).satisfies(createData -> {
                assertThat(createData.getEntityName()).isEqualTo(PERSON.getName());
                assertThat(createData.getAttributes()).containsExactlyInAnyOrder(
                        new SimpleAttributeData<>(PERSON_NAME.getName(), "test"),
                        new SimpleAttributeData<>(PERSON_VAT.getName(), "123456"),
                        new SimpleAttributeData<>(PERSON_AGE.getName(), null),
                        new SimpleAttributeData<>(PERSON_GENDER.getName(), null),
                        new SimpleAttributeData<>(PERSON_TAGS.getName(), List.of())
                );

                assertThat(createData.getRelations()).isEmpty();
            });

            Mockito.verifyNoInteractions(contentStore);
        }

        @Test
        void auditMetadata_onCreate() throws InvalidPropertyDataException {
            var createDataCaptor = ArgumentCaptor.forClass(EntityCreateData.class);
            var entityId = EntityId.of(UUID.randomUUID());
            var personId = EntityId.of(UUID.randomUUID());
            Mockito.when(queryEngine.create(Mockito.any(), createDataCaptor.capture(), Mockito.any(), Mockito.any()))
                    .thenReturn(EntityData.builder().name(INVOICE.getName()).id(entityId).build());

            var result = datamodelApi.create(APPLICATION, INVOICE.getName(), MapRequestInputData.fromMap(Map.of(
                    "number", "1",
                    "amount", 1.50,
                    "confidentiality", "public",
                    "customer", new RelationDataEntry(PERSON.getName(), personId)
            )), AuthorizationContext.allowAll(
                    new User("00000000-0000-0000-0000-000000000000", "keycloak", "alice@example.com")
            ));

            assertThat(result.getIdentity().getEntityId()).isEqualTo(entityId);

            assertThat(createDataCaptor.getValue()).satisfies(createData -> {
                assertThat(createData.getEntityName()).isEqualTo(INVOICE.getName());
                assertThat(createData.getAttributes())
                        .anySatisfy(data -> {
                            assertThat(data.getName()).isEqualTo(AttributeName.of("audit_metadata"));
                            assertThat(subValueMatches(data, "created_date", Instant.now(clock))).isTrue();
                            assertThat(subValueMatches(data, "last_modified_date", Instant.now(clock))).isTrue();
                            assertThat(((CompositeAttributeData) data).getAttributes())
                                    .anyMatch(sub -> sub.getName().equals(AttributeName.of("created_by"))
                                            &&  subValueMatches(sub, "name", "alice@example.com"))
                                    .anyMatch(sub -> sub.getName().equals(AttributeName.of("last_modified_by"))
                                            && subValueMatches(sub, "name", "alice@example.com"))
                            ;
                        });
            });

            Mockito.verifyNoInteractions(contentStore);
        }

        @Test
        void auditMetadata_onUpdate() throws InvalidPropertyDataException {
            var updateDataCaptor = ArgumentCaptor.forClass(EntityData.class);
            var entityId = EntityId.of(UUID.randomUUID());
            var personId = EntityId.of(UUID.randomUUID());
            Mockito.when(queryEngine.update(Mockito.any(), updateDataCaptor.capture(), Mockito.any(), Mockito.any()))
                    .thenReturn(new UpdateResult(
                            EntityData.builder().name(INVOICE.getName()).id(entityId).build(),
                            EntityData.builder().name(INVOICE.getName()).id(entityId).build()
                    ));
            var existing = new InternalEntityInstance(
                    EntityIdentity.forEntity(INVOICE.getName(), entityId),
                    new LinkedHashMap<>(),
                    List.of(),
                    List.of());

            var result = datamodelApi.update(APPLICATION, existing, MapRequestInputData.fromMap(Map.of(
                    "number", "1",
                    "amount", 1.50,
                    "confidentiality", "public",
                    "customer", new RelationDataEntry(PERSON.getName(), personId)
            )), AuthorizationContext.allowAll(
                    new User("00000000-0000-0000-0000-000000000000", "keycloak", "alice@example.com")
            ));

            assertThat(result.getIdentity().getEntityId()).isEqualTo(entityId);

            assertThat(updateDataCaptor.getValue()).satisfies(updateData -> {
                assertThat(updateData.getAttributes())
                        .anySatisfy(data -> {
                            assertThat(data.getName()).isEqualTo(AttributeName.of("audit_metadata"));
                            assertThat(subValueMatches(data, "last_modified_date", Instant.now(clock))).isTrue();
                            assertThat(((CompositeAttributeData) data).getAttributes())
                                    .noneMatch(sub -> sub.getName().equals(AttributeName.of("created_by")))
                                    .noneMatch(sub -> sub.getName().equals(AttributeName.of("created_date")))
                                    .anyMatch(sub -> sub.getName().equals(AttributeName.of("last_modified_by"))
                                            && subValueMatches(sub, "name", "alice@example.com"))
                            ;
                        });
            });

            Mockito.verifyNoInteractions(contentStore);
        }

        @Test
        void auditMetadata_onPartialUpdate() throws InvalidPropertyDataException {
            var updateDataCaptor = ArgumentCaptor.forClass(EntityData.class);
            var entityId = EntityId.of(UUID.randomUUID());
            var personId = EntityId.of(UUID.randomUUID());
            Mockito.when(queryEngine.update(Mockito.any(), updateDataCaptor.capture(), Mockito.any(), Mockito.any()))
                    .thenReturn(new UpdateResult(
                            EntityData.builder().name(INVOICE.getName()).id(entityId).build(),
                            EntityData.builder().name(INVOICE.getName()).id(entityId).build()
                    ));
            var existing = new InternalEntityInstance(
                    EntityIdentity.forEntity(INVOICE.getName(), entityId),
                    new LinkedHashMap<>(),
                    List.of(),
                    List.of());

            var result = datamodelApi.updatePartial(APPLICATION, existing, MapRequestInputData.fromMap(Map.of(
                    "number", "1",
                    "amount", 1.50,
                    "confidentiality", "public",
                    "customer", new RelationDataEntry(PERSON.getName(), personId)
            )), AuthorizationContext.allowAll(
                    new User("00000000-0000-0000-0000-000000000000", "keycloak", "alice@example.com")
            ));

            assertThat(result.getIdentity().getEntityId()).isEqualTo(entityId);

            assertThat(updateDataCaptor.getValue()).satisfies(updateData -> {
                assertThat(updateData.getAttributes())
                        .anySatisfy(data -> {
                            assertThat(data.getName()).isEqualTo(AttributeName.of("audit_metadata"));
                            assertThat(subValueMatches(data, "last_modified_date", Instant.now(clock))).isTrue();
                            assertThat(((CompositeAttributeData) data).getAttributes())
                                    .noneMatch(sub -> sub.getName().equals(AttributeName.of("created_by")))
                                    .noneMatch(sub -> sub.getName().equals(AttributeName.of("created_date")))
                                    .anyMatch(sub -> sub.getName().equals(AttributeName.of("last_modified_by"))
                                            && subValueMatches(sub, "name", "alice@example.com"))
                            ;
                        });
            });

            Mockito.verifyNoInteractions(contentStore);
        }


        private static boolean subValueMatches(AttributeData data, String subAttrName, Object value) {
            if (data instanceof CompositeAttributeData composite) {
                var sub = composite.getAttributeByName(AttributeName.of(subAttrName));
                if (sub.isEmpty()) {
                    return false;
                }
                if (sub.get() instanceof SimpleAttributeData<?> simpData) {
                    return value.equals(simpData.getValue());
                }
            }
            return false;
        }

        @ParameterizedTest
        @MethodSource
        void incorrectRelation_fails(Object customer, Object products) {

            assertThatThrownBy(() -> {
                 datamodelApi.create(APPLICATION, INVOICE.getName(), MapRequestInputData.fromMap(Map.of(
                        "number", "1",
                        "amount", 1.50,
                        "confidentiality", "public",
                        "customer", customer,
                        "products", products
                 )), AuthorizationContext.allowAll());
            }).isInstanceOfSatisfying(InvalidPropertyDataException.class, exception -> {
                assertThat(exception.allExceptions())
                        .allSatisfy(ex -> {
                            assertThat(ex.getCause()).isInstanceOf(InvalidDataTypeException.class);
                        })
                        .extracting(e -> e.getPath().toString())
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
            Mockito.when(queryEngine.create(Mockito.any(), createDataCaptor.capture(), Mockito.any(), Mockito.any()))
                    .thenReturn(EntityData.builder().name(INVOICE.getName()).id(entityId).build());
            Mockito.when(contentStore.writeContent(Mockito.any())).thenAnswer(contentAccessorFor(fileId));

            var result = datamodelApi.create(APPLICATION, INVOICE.getName(), MapRequestInputData.fromMap(Map.of(
                    "number", "1",
                    "amount", 1.50,
                    "confidentiality", "public",
                    "customer", new RelationDataEntry(PERSON.getName(), personId),
                    "content", new FileDataEntry("my-file.pdf", "application/pdf", inputStreamWithSize(110))
            )), AuthorizationContext.allowAll());

            assertThat(result.getIdentity().getEntityId()).isEqualTo(entityId);

            assertThat(createDataCaptor.getValue()).satisfies(createData -> {
                assertThat(createData.getEntityName()).isEqualTo(INVOICE.getName());
                assertThat(createData.getAttributes())
                        .containsExactlyInAnyOrder(
                                new SimpleAttributeData<>(INVOICE_NUMBER.getName(), "1"),
                                new SimpleAttributeData<>(INVOICE_AMOUNT.getName(), BigDecimal.valueOf(1.50)),
                                new SimpleAttributeData<>(INVOICE_RECEIVED.getName(), null),
                                new SimpleAttributeData<>(INVOICE_PAY_BEFORE.getName(), null),
                                new SimpleAttributeData<>(INVOICE_PAY_TIMESTAMP.getName(), null),
                                new SimpleAttributeData<>(INVOICE_IS_PAID.getName(), null),
                                new SimpleAttributeData<>(INVOICE_CONFIDENTIALITY.getName(), "public"),
                        CompositeAttributeData.builder()
                                .name(INVOICE_CONTENT.getName())
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getId().getName(), fileId))
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getFilename().getName(), "my-file.pdf"))
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getMimetype().getName(), "application/pdf"))
                                .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getLength().getName(), 110L))
                                .build(),
                                getAuditMetadataData(true)
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
                    "number", "1",
                    "amount", 1.50,
                    "confidentiality", "public",
                    "customer", new RelationDataEntry(PERSON.getName(), personId),
                    "content", Map.of(
                            "id", "123",
                            "filename", "test-file.pdf",
                            "mimetype", "application/pdf",
                            "length", 120
                    )
            )), AuthorizationContext.allowAll()))
                    .isInstanceOfSatisfying(InvalidPropertyDataException.class, e -> {
                        assertThat(e.getPath()).hasToString("content");
                    });

            Mockito.verifyNoInteractions(contentStore, queryEngine);
        }
    }

    private static Answer<ContentAccessor> contentAccessorFor(String fileId) {
        return invocation -> {
            InputStream inputStream = invocation.getArgument(0);
            inputStream.readAllBytes(); // read the bytes, so the underlying CountingInputStream has the correct size
            var ca = Mockito.mock(ContentAccessor.class, Answers.RETURNS_SMART_NULLS);
            Mockito.when(ca.getReference()).thenReturn(ContentReference.of(fileId));
            return ca;
        };
    }

    private static InputStreamSupplier inputStreamWithSize(int size) {
        return () -> new ByteArrayInputStream(new byte[size]);
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
            Mockito.when(queryEngine.update(Mockito.any(), createDataCaptor.capture(), Mockito.any(), Mockito.any()))
                    .thenReturn(new UpdateResult(entity, entity));
            datamodelApi.update(APPLICATION, EntityRequest.forEntity(INVOICE.getName(), entityId),
                    MapRequestInputData.fromMap(Map.of(
                            "number", "1",
                            "amount", 1.50,
                            "received", LocalDate.now(clock),
                            "confidentiality", "public",
                            "pay_before", NullDataEntry.INSTANCE, // Non-required value set to null
                            "is_paid", MissingDataEntry.INSTANCE // Non-required value is missing completely
                    )),
                    AuthorizationContext.allowAll()
            );

            assertThat(createDataCaptor.getValue().getId()).isEqualTo(entityId);
            assertThat(createDataCaptor.getValue().getName()).isEqualTo(INVOICE.getName());
            assertThat(createDataCaptor.getValue().getAttributes()).containsExactlyInAnyOrder(
                    new SimpleAttributeData<>(INVOICE_NUMBER.getName(), "1"),
                    new SimpleAttributeData<>(INVOICE_AMOUNT.getName(), BigDecimal.valueOf(1.50)),
                    new SimpleAttributeData<>(INVOICE_RECEIVED.getName(), LocalDate.now(clock)),
                    new SimpleAttributeData<>(INVOICE_CONFIDENTIALITY.getName(), "public"),
                    new SimpleAttributeData<>(INVOICE_PAY_BEFORE.getName(), null), // Is set to null
                    new SimpleAttributeData<>(INVOICE_PAY_TIMESTAMP.getName(), null), // Is also set to null
                    new SimpleAttributeData<>(INVOICE_IS_PAID.getName(), null), // Is also set to null during an update
                    CompositeAttributeData.builder()
                            .name(INVOICE_CONTENT.getName())
                            .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getId().getName(), null))
                            .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getFilename().getName(), null))
                            .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getMimetype().getName(), null))
                            .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getLength().getName(), null))
                            .build(),
                    getAuditMetadataData(false)
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
                                "received", LocalDate.now(clock),
                                "confidentiality", "public"
                        )),
                        AuthorizationContext.allowAll()
                );
            }).isInstanceOfSatisfying(InvalidPropertyDataException.class, exception -> {
                assertThat(exception.allExceptions())
                        .allSatisfy(ex -> {
                            assertThat(ex.getCause()).isInstanceOf(RequiredConstraintViolationInvalidDataException.class);
                        })
                        .extracting(e -> e.getPath().toString())
                        .containsExactlyInAnyOrder(
                                "amount",
                                "number"
                        );
            });

            Mockito.verify(queryEngine, Mockito.never()).update(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
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
            Mockito.when(queryEngine.update(Mockito.any(), createDataCaptor.capture(), Mockito.any(), Mockito.any()))
                    .thenReturn(new UpdateResult(entity, entity));
            datamodelApi.update(APPLICATION, EntityRequest.forEntity(INVOICE.getName(), entityId),
                    MapRequestInputData.fromMap(Map.of(
                            "number", "1",
                            "amount", 1.50,
                            "confidentiality", "public",
                            "content", Map.of(
                                    "filename", "file-123.pdf",
                                    "mimetype", "application/pdf",
                                    "id", "will-be-ignored",
                                    "length", 0xbad
                            )
                    )),
                    AuthorizationContext.allowAll()
            );

            assertThat(createDataCaptor.getValue().getId()).isEqualTo(entityId);
            assertThat(createDataCaptor.getValue().getName()).isEqualTo(INVOICE.getName());
            assertThat(createDataCaptor.getValue().getAttributes()).containsExactlyInAnyOrder(
                    new SimpleAttributeData<>(INVOICE_NUMBER.getName(), "1"),
                    new SimpleAttributeData<>(INVOICE_AMOUNT.getName(), BigDecimal.valueOf(1.50)),
                    new SimpleAttributeData<>(INVOICE_CONFIDENTIALITY.getName(), "public"),
                    // Missing values are set to null
                    new SimpleAttributeData<>(INVOICE_RECEIVED.getName(), null),
                    new SimpleAttributeData<>(INVOICE_PAY_BEFORE.getName(), null),
                    new SimpleAttributeData<>(INVOICE_PAY_TIMESTAMP.getName(), null),
                    new SimpleAttributeData<>(INVOICE_IS_PAID.getName(), null),
                    CompositeAttributeData.builder()
                            .name(INVOICE_CONTENT.getName())
                            // Note, content ID & length are not updated/overwritten ever
                            .attribute(
                                    new SimpleAttributeData<>(INVOICE_CONTENT.getFilename().getName(), "file-123.pdf"))
                            .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getMimetype().getName(),
                                    "application/pdf"))
                            .build(),
                    getAuditMetadataData(false)
            );
        }

        @Test
        void contentAttributes_withoutContent_fails() {
            var entityId = EntityId.of(UUID.randomUUID());
            setupEntityQueryWithContent(null);
            assertThatThrownBy(() -> {
                datamodelApi.update(APPLICATION, EntityRequest.forEntity(INVOICE.getName(), entityId),
                        MapRequestInputData.fromMap(Map.of(
                                "number", "1",
                                "amount", 1.50,
                                "content", Map.of(
                                        "filename", "file-123.pdf",
                                        "mimetype", "application/pdf",
                                        "id", "will-be-ignored",
                                        "length", 0xbad
                                )
                        )),
                        AuthorizationContext.allowAll()
                );
            }).isInstanceOfSatisfying(InvalidPropertyDataException.class, exception -> {
                assertThat(exception.allExceptions())
                        .anySatisfy(ex -> {
                            assertThat(ex.getCause()).isInstanceOf(ContentMissingInvalidDataException.class);
                            assertThat(ex.getPath()).hasToString("content");
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
                                "number", "1",
                                "amount", 1.50,
                                "content", Map.of(
                                        "filename", "file-123.pdf",
                                        "mimetype", dataEntry
                                )
                        )),
                        AuthorizationContext.allowAll()
                );
            }).isInstanceOfSatisfying(InvalidPropertyDataException.class, exception -> {
                assertThat(exception.allExceptions())
                        .anySatisfy(ex -> {
                            assertThat(ex.getCause()).isInstanceOf(RequiredConstraintViolationInvalidDataException.class);
                            assertThat(ex.getPath()).hasToString("content.mimetype");
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
            Mockito.when(queryEngine.update(Mockito.any(), createDataCaptor.capture(), Mockito.any(), Mockito.any()))
                    .thenReturn(new UpdateResult(entity, entity));
            datamodelApi.update(APPLICATION, EntityRequest.forEntity(INVOICE.getName(), entityId),
                    MapRequestInputData.fromMap(Map.of(
                            "number", "1",
                            "amount", 1.50,
                            "confidentiality", "public",
                            "content", Map.of(
                                    "filename", dataEntry,
                                    "mimetype", "application/pdf"
                            )
                    )),
                    AuthorizationContext.allowAll()
            );

            assertThat(createDataCaptor.getValue().getId()).isEqualTo(entityId);
            assertThat(createDataCaptor.getValue().getName()).isEqualTo(INVOICE.getName());
            assertThat(createDataCaptor.getValue().getAttributes()).containsExactlyInAnyOrder(
                    new SimpleAttributeData<>(INVOICE_NUMBER.getName(), "1"),
                    new SimpleAttributeData<>(INVOICE_AMOUNT.getName(), BigDecimal.valueOf(1.50)),
                    new SimpleAttributeData<>(INVOICE_CONFIDENTIALITY.getName(), "public"),
                    // Missing values are set to null
                    new SimpleAttributeData<>(INVOICE_RECEIVED.getName(), null),
                    new SimpleAttributeData<>(INVOICE_PAY_BEFORE.getName(), null),
                    new SimpleAttributeData<>(INVOICE_PAY_TIMESTAMP.getName(), null),
                    new SimpleAttributeData<>(INVOICE_IS_PAID.getName(), null),
                    CompositeAttributeData.builder()
                            .name(INVOICE_CONTENT.getName())
                            // Content ID and size are kept/not overwritten, so they are not present here
                            .attribute(
                                    new SimpleAttributeData<>(INVOICE_CONTENT.getFilename().getName(), null))
                            .attribute(new SimpleAttributeData<>(INVOICE_CONTENT.getMimetype().getName(),
                                    "application/pdf"))
                            .build(),
                    getAuditMetadataData(false)
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
            Mockito.when(queryEngine.update(Mockito.any(), createDataCaptor.capture(), Mockito.any(), Mockito.any()))
                    .thenReturn(new UpdateResult(entity, entity));

            Mockito.when(contentStore.writeContent(Mockito.any())).thenAnswer(contentAccessorFor(fileId));

            datamodelApi.update(APPLICATION, EntityRequest.forEntity(INVOICE.getName(), entityId),
                    MapRequestInputData.fromMap(Map.of(
                            "number", "1",
                            "amount", 1.50,
                            "confidentiality", "public",
                            "content", new FileDataEntry("my-file.pdf", "application/pdf", inputStreamWithSize(50))
                    )),
                    AuthorizationContext.allowAll()
            );

            assertThat(createDataCaptor.getValue().getId()).isEqualTo(entityId);
            assertThat(createDataCaptor.getValue().getName()).isEqualTo(INVOICE.getName());
            assertThat(createDataCaptor.getValue().getAttributes()).containsExactlyInAnyOrder(
                    new SimpleAttributeData<>(INVOICE_NUMBER.getName(), "1"),
                    new SimpleAttributeData<>(INVOICE_AMOUNT.getName(), BigDecimal.valueOf(1.50)),
                    new SimpleAttributeData<>(INVOICE_CONFIDENTIALITY.getName(), "public"),
                    // Missing values are set to null
                    new SimpleAttributeData<>(INVOICE_RECEIVED.getName(), null),
                    new SimpleAttributeData<>(INVOICE_PAY_BEFORE.getName(), null),
                    new SimpleAttributeData<>(INVOICE_PAY_TIMESTAMP.getName(), null),
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
                            .build(),
                    getAuditMetadataData(false)
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
            Mockito.when(queryEngine.update(Mockito.any(), createDataCaptor.capture(), Mockito.any(), Mockito.any()))
                    .thenReturn(new UpdateResult(entity, entity));
            datamodelApi.updatePartial(APPLICATION, EntityRequest.forEntity(INVOICE.getName(), entityId),
                    MapRequestInputData.fromMap(Map.of(
                            "number", "1",
                            "amount", MissingDataEntry.INSTANCE, // Required value is missing completely
                            "confidentiality", "public",
                            "received", LocalDate.now(clock),
                            "pay_before", NullDataEntry.INSTANCE, // Non-required value set to null
                            "pay_timestamp", Instant.now(clock),
                            "is_paid", MissingDataEntry.INSTANCE // Non-required value is missing completely
                    )),
                    AuthorizationContext.allowAll()
            );

            assertThat(createDataCaptor.getValue().getId()).isEqualTo(entityId);
            assertThat(createDataCaptor.getValue().getName()).isEqualTo(INVOICE.getName());
            assertThat(createDataCaptor.getValue().getAttributes()).containsExactlyInAnyOrder(
                    new SimpleAttributeData<>(INVOICE_NUMBER.getName(), "1"),
                    // amount is missing here, and thus not overwritten
                    new SimpleAttributeData<>(INVOICE_CONFIDENTIALITY.getName(), "public"),
                    new SimpleAttributeData<>(INVOICE_RECEIVED.getName(), LocalDate.now(clock)),
                    new SimpleAttributeData<>(INVOICE_PAY_BEFORE.getName(), null), // Is set to null
                    new SimpleAttributeData<>(INVOICE_PAY_TIMESTAMP.getName(), Instant.now(clock)),
                    // is_paid is missing here, and thus not overwritten
                    getAuditMetadataData(false)
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
                        )), AuthorizationContext.allowAll());
            }).isInstanceOfSatisfying(InvalidPropertyDataException.class, exception -> {
                assertThat(exception.allExceptions())
                        .allSatisfy(ex -> {
                            assertThat(ex.getCause()).isInstanceOf(RequiredConstraintViolationInvalidDataException.class);
                        })
                        .extracting(e -> e.getPath().toString())
                        .containsExactlyInAnyOrder(
                                "number"
                        );
            });

            Mockito.verify(queryEngine, Mockito.never()).update(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
            Mockito.verifyNoInteractions(contentStore);
        }

        @Test
        void notAllowedValue_fails() {
            setupEntityQuery();
            assertThatThrownBy(() -> {
                datamodelApi.updatePartial(APPLICATION,
                        EntityRequest.forEntity(INVOICE.getName(), EntityId.of(UUID.randomUUID())),
                        MapRequestInputData.fromMap(Map.of(
                                "confidentiality", "abc"
                        )), AuthorizationContext.allowAll());
            }).isInstanceOfSatisfying(InvalidPropertyDataException.class, exception -> {
                assertThat(exception.allExceptions())
                        .allSatisfy(ex -> {
                            assertThat(ex.getCause()).isInstanceOf(AllowedValuesConstraintViolationInvalidDataException.class);
                        })
                        .extracting(e -> e.getPath().toString())
                        .containsExactlyInAnyOrder(
                                "confidentiality"
                        );
            });

            Mockito.verify(queryEngine, Mockito.never()).update(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
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
            Mockito.when(queryEngine.update(Mockito.any(), createDataCaptor.capture(), Mockito.any(), Mockito.any()))
                    .thenReturn(new UpdateResult(entity, entity));
            datamodelApi.updatePartial(APPLICATION, EntityRequest.forEntity(INVOICE.getName(), entityId),
                    MapRequestInputData.fromMap(Map.of(
                    "customer", NullDataEntry.INSTANCE // Relation is set to null; but updates do not affect relations
                    )), AuthorizationContext.allowAll());

            assertThat(createDataCaptor.getValue().getId()).isEqualTo(entityId);
            assertThat(createDataCaptor.getValue().getName()).isEqualTo(INVOICE.getName());
            assertThat(createDataCaptor.getValue().getAttributes()).containsExactlyInAnyOrder(getAuditMetadataData(false));

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

            Mockito.when(queryEngine.update(Mockito.any(), createDataCaptor.capture(), Mockito.any(), Mockito.any()))
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
                    AuthorizationContext.allowAll()
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
                            .build(),
                    getAuditMetadataData(false)
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
                        "number", "1",
                        "amount", 1.50,
                        "confidentiality", "public",
                        "content", Map.of(
                                "filename", "file-123.pdf",
                                "mimetype", "application/pdf",
                                "id", "will-be-ignored",
                                "length", 0xbad
                        )
                        )),
                        AuthorizationContext.allowAll()
                );
            }).isInstanceOfSatisfying(InvalidPropertyDataException.class, exception -> {
                assertThat(exception.allExceptions())
                        .anySatisfy(ex -> {
                            assertThat(ex.getCause()).isInstanceOf(ContentMissingInvalidDataException.class);
                            assertThat(ex.getPath()).hasToString("content");
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
                        AuthorizationContext.allowAll()
                );
            }).isInstanceOfSatisfying(InvalidPropertyDataException.class, exception -> {
                assertThat(exception.allExceptions())
                        .anySatisfy(ex -> {
                            assertThat(ex.getCause()).isInstanceOf(RequiredConstraintViolationInvalidDataException.class);
                            assertThat(ex.getPath()).hasToString("content.mimetype");
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
            Mockito.when(queryEngine.update(Mockito.any(), createDataCaptor.capture(), Mockito.any(), Mockito.any()))
                    .thenReturn(new UpdateResult(entity, entity));
            datamodelApi.updatePartial(APPLICATION, EntityRequest.forEntity(INVOICE.getName(), entityId),
                    MapRequestInputData.fromMap(Map.of(
                            "content", Map.of(
                                    "filename", "test132.pdf",
                                    "mimetype", MissingDataEntry.INSTANCE
                            )
                    )),
                    AuthorizationContext.allowAll()
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
                            .build(),
                    getAuditMetadataData(false)
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
            Mockito.when(queryEngine.update(Mockito.any(), createDataCaptor.capture(), Mockito.any(), Mockito.any()))
                    .thenReturn(new UpdateResult(entity, entity));
            datamodelApi.updatePartial(APPLICATION, EntityRequest.forEntity(INVOICE.getName(), entityId),
                    MapRequestInputData.fromMap(Map.of(
                            "content", Map.of(
                                    "filename", NullDataEntry.INSTANCE,
                                    "mimetype", "application/pdf"
                            )
                    )),
                    AuthorizationContext.allowAll()
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
                            .build(),
                    getAuditMetadataData(false)
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
            Mockito.when(queryEngine.update(Mockito.any(), createDataCaptor.capture(), Mockito.any(), Mockito.any()))
                    .thenReturn(new UpdateResult(entity, entity));
            datamodelApi.updatePartial(APPLICATION, EntityRequest.forEntity(INVOICE.getName(), entityId),
                    MapRequestInputData.fromMap(Map.of(
                            "content", Map.of(
                                    "filename", MissingDataEntry.INSTANCE,
                                    "mimetype", "application/pdf"
                            )
                    )),
                    AuthorizationContext.allowAll()
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
                            .build(),
                    getAuditMetadataData(false)
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
            Mockito.when(queryEngine.update(Mockito.any(), createDataCaptor.capture(), Mockito.any(), Mockito.any()))
                    .thenReturn(new UpdateResult(entity, entity));
            Mockito.when(contentStore.writeContent(Mockito.any())).thenAnswer(contentAccessorFor(fileId));
            datamodelApi.updatePartial(APPLICATION, EntityRequest.forEntity(INVOICE.getName(), entityId),
                    MapRequestInputData.fromMap(Map.of(
                    "content", new FileDataEntry("my-file.pdf", "application/pdf", inputStreamWithSize(150))
                    )), AuthorizationContext.allowAll());

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
                            .build(),
                    getAuditMetadataData(false)
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
            var firstPage = datamodelApi.findAll(APPLICATION, INVOICE, PARAMS, new EncodedCursorPagination(null, 20, SortData.unsorted()), AuthorizationContext.allowAll());
            assertEquals(100.0, getAmount(firstPage.getContent().getFirst()));
            assertEquals(2000.0, getAmount(firstPage.getContent().getLast()));

            assertNotNull(firstPage.next().orElse(null));
            assertNull(firstPage.previous().orElse(null));

            // get the cursor for the next page from the result of the first page
            EncodedCursorPagination nextPageRequest = (EncodedCursorPagination) firstPage.getControls().next().orElseThrow();

            var secondPage = datamodelApi.findAll(APPLICATION, INVOICE, PARAMS, nextPageRequest, AuthorizationContext.allowAll());
            assertEquals(2100.0, getAmount(secondPage.getContent().getFirst()));
            assertEquals(4000.0, getAmount(secondPage.getContent().getLast()));

            assertNotNull(secondPage.next().orElse(null));
            assertNotNull(secondPage.previous().orElse(null));

            nextPageRequest = (EncodedCursorPagination) secondPage.next().orElseThrow();

            var thirdPage = datamodelApi.findAll(APPLICATION, INVOICE, PARAMS, nextPageRequest, AuthorizationContext.allowAll());
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
            var firstPage = datamodelApi.findAll(APPLICATION, INVOICE, PARAMS, new EncodedCursorPagination(null, 50, SortData.unsorted()), AuthorizationContext.allowAll());
            assertEquals(100.0, getAmount(firstPage.getContent().getFirst()));
            assertEquals(5000.0, getAmount(firstPage.getContent().getLast()));

            assertNotNull(firstPage.next().orElse(null));
            assertNull(firstPage.previous().orElse(null));

            // get the cursor for the next page from the result of the first page
            EncodedCursorPagination nextPageRequest = (EncodedCursorPagination) firstPage.getControls().next().orElseThrow();

            var secondPage = datamodelApi.findAll(APPLICATION, INVOICE, PARAMS, nextPageRequest, AuthorizationContext.allowAll());
            assertEquals(5_100.0, getAmount(secondPage.getContent().getFirst()));
            assertEquals(10_000.0, getAmount(secondPage.getContent().getLast()));

            assertNotNull(secondPage.next().orElse(null));
            assertNotNull(secondPage.previous().orElse(null));

            nextPageRequest = (EncodedCursorPagination) secondPage.next().orElseThrow();

            var thirdPage = datamodelApi.findAll(APPLICATION, INVOICE, PARAMS, nextPageRequest, AuthorizationContext.allowAll());
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
            var firstPage = datamodelApi.findAll(APPLICATION, INVOICE, Map.of("confidentiality", List.of("public")),
                    new EncodedCursorPagination(null, 20, SortData.unsorted()), AuthorizationContext.allowAll());
            assertEquals(100.0, getAmount(firstPage.getContent().getFirst()));
            assertEquals(3900.0, getAmount(firstPage.getContent().getLast()));

            // get the cursor for the next page from the result of the first page
            EncodedCursorPagination nextPageRequest = (EncodedCursorPagination) firstPage.getControls().next().orElseThrow();

            var secondPage = datamodelApi.findAll(APPLICATION, INVOICE, Map.of("confidentiality", List.of("public")),
                    nextPageRequest, AuthorizationContext.allowAll());
            assertEquals(4100.0, getAmount(secondPage.getContent().getFirst()));
            assertEquals(7900.0, getAmount(secondPage.getContent().getLast()));

            nextPageRequest = (EncodedCursorPagination) secondPage.getControls().next().orElseThrow();

            var thirdPage = datamodelApi.findAll(APPLICATION, INVOICE, Map.of("confidentiality", List.of("public")),
                    nextPageRequest, AuthorizationContext.allowAll());
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
            var firstPage = datamodelApi.findAll(APPLICATION, INVOICE, PARAMS, new EncodedCursorPagination(null, 20, sort), AuthorizationContext.allowAll());
            assertEquals(100_000_000.0, getAmount(firstPage.getContent().getFirst()));
            assertEquals(99_998_100.0, getAmount(firstPage.getContent().getLast()));

            // get the cursor for the next page from the result of the first page
            EncodedCursorPagination nextPageRequest = (EncodedCursorPagination) firstPage.getControls().next().orElseThrow();

            var secondPage = datamodelApi.findAll(APPLICATION, INVOICE, PARAMS, nextPageRequest, AuthorizationContext.allowAll());
            assertEquals(99_998_000.0, getAmount(secondPage.getContent().getFirst()));
            assertEquals(99_996_100.0, getAmount(secondPage.getContent().getLast()));

            nextPageRequest = (EncodedCursorPagination) secondPage.getControls().next().orElseThrow();

            var thirdPage = datamodelApi.findAll(APPLICATION, INVOICE, PARAMS, nextPageRequest, AuthorizationContext.allowAll());
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
            var startPage = datamodelApi.findAll(APPLICATION, INVOICE, PARAMS, new EncodedCursorPagination(null, 20, SortData.unsorted()), AuthorizationContext.allowAll());

            // Navigate to third page (next page is tested in other tests)
            var secondPage = datamodelApi.findAll(APPLICATION, INVOICE, PARAMS, (EncodedCursorPagination) startPage.next().orElseThrow(), AuthorizationContext.allowAll());
            var thirdPage = datamodelApi.findAll(APPLICATION, INVOICE, PARAMS, (EncodedCursorPagination) secondPage.next().orElseThrow(), AuthorizationContext.allowAll());

            // Verify that navigating to current page remains the same
            var currentPage = datamodelApi.findAll(APPLICATION, INVOICE, PARAMS, (EncodedCursorPagination) thirdPage.current(), AuthorizationContext.allowAll());
            assertEquals(getAmount(thirdPage.getContent().getFirst()), getAmount(currentPage.getContent().getFirst()));
            assertEquals(getAmount(thirdPage.getContent().getLast()), getAmount(currentPage.getContent().getLast()));

            // Verify that previous page is the same as second page
            var prevPage = datamodelApi.findAll(APPLICATION, INVOICE, PARAMS, (EncodedCursorPagination) thirdPage.previous().orElseThrow(), AuthorizationContext.allowAll());
            assertEquals(getAmount(secondPage.getContent().getFirst()), getAmount(prevPage.getContent().getFirst()));
            assertEquals(getAmount(secondPage.getContent().getLast()), getAmount(prevPage.getContent().getLast()));

            // Verify that first page is the same as starting page
            var firstPage = datamodelApi.findAll(APPLICATION, INVOICE, PARAMS, (EncodedCursorPagination) thirdPage.first(), AuthorizationContext.allowAll());
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

            var result = datamodelApi.findAll(APPLICATION, INVOICE, PARAMS, new EncodedCursorPagination(fakeCursor(page), size, SortData.unsorted()), AuthorizationContext.allowAll());
            assertEquals(expected, result.getTotalItemCount());

            // assert count was not called when stubNeeded is false
            Mockito.verifyNoMoreInteractions(queryEngine);
        }

        private double getAmount(EntityInstance entity) {
            var data = entity.getData().get(INVOICE_AMOUNT.getName().getValue());
            return ((DecimalDataEntry) data).getValue().doubleValue();
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
                    .attribute(SimpleAttributeData.<BigDecimal>builder()
                            .name(INVOICE_AMOUNT.getName())
                            .value(BigDecimal.valueOf(i * 100.0))
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
    class EntityLinks {

        private static final SimpleAttribute DOCUMENT_ID = SimpleAttribute.builder()
                .name(AttributeName.of("id"))
                .column(ColumnName.of("id"))
                .type(Type.UUID)
                .flag(ReadOnlyFlag.INSTANCE)
                .build();

        private static final SimpleAttribute DOCUMENT_CATEGORY = SimpleAttribute.builder()
                .name(AttributeName.of("category"))
                .column(ColumnName.of("category"))
                .type(Type.TEXT)
                .build();

        private static final ContentAttribute DOCUMENT_ATTACHMENT = ContentAttribute.builder()
                .name(AttributeName.of("attachment"))
                .pathSegment(PathSegmentName.of("attachment"))
                .linkName(LinkName.of("attachment"))
                .idColumn(ColumnName.of("attachment__id"))
                .filenameColumn(ColumnName.of("attachment__filename"))
                .mimetypeColumn(ColumnName.of("attachment__mimetype"))
                .lengthColumn(ColumnName.of("attachment__length"))
                .build();

        // Uses all entity substitution variables and the owner value of an attribute
        private static final EntityLink CATEGORY_LINK = EntityLink.builder()
                .identity(new NamedLink(URI.create("https://links.example/rel/category"), "category"))
                .profile(URI.create("https://links.example/profile/category"))
                .owner(new SimpleAttributePath(DOCUMENT_CATEGORY.getName()))
                .fallbackTemplate(template(
                        "https://categories.example/%{entity.name}?name=%{owner.name}&value=%{owner.value}&id=%{entity.id}"))
                .build();

        // Uses only the entity link, without an owner
        private static final EntityLink PREVIEW_LINK = EntityLink.builder()
                .identity(new UnnamedLink(URI.create("https://links.example/rel/preview")))
                .fallbackTemplate(template("https://preview.example/render?src=%{entity.link}"))
                .build();

        // Uses the owner link of a content attribute
        private static final EntityLink ATTACHMENT_SCAN_LINK = EntityLink.builder()
                .identity(new NamedLink(URI.create("https://links.example/rel/scan"), "attachment"))
                .owner(new SimpleAttributePath(DOCUMENT_ATTACHMENT.getName()))
                .fallbackTemplate(template("https://scanner.example/scan?content=%{owner.link}"))
                .build();

        // Uses the owner link and name of a relation
        private static final EntityLink AUTHOR_LINK = EntityLink.builder()
                .identity(new NamedLink(URI.create("https://links.example/rel/author-info"), "author"))
                .owner(new SimpleRelationPath(RelationName.of("author")))
                .fallbackTemplate(template("https://people.example/info?me=%{owner.link}&relation=%{owner.name}"))
                .build();

        // References an automation system and base path name that are registered in the configuration
        private static final EntityLink AUTOMATION_LINK = EntityLink.builder()
                .identity(new NamedLink(URI.create("https://links.example/rel/automation"), "automation"))
                .fallbackTemplate(automationTemplate("my-automation", "api",
                        "/documents/%{entity.id}?app=%{application.id}"))
                .build();

        // References an automation system that is not registered in the configuration
        private static final EntityLink UNKNOWN_AUTOMATION_LINK = EntityLink.builder()
                .identity(new NamedLink(URI.create("https://links.example/rel/automation"), "unknown-automation"))
                .fallbackTemplate(automationTemplate("unknown-automation", "api",
                        "/documents/%{entity.id}"))
                .build();

        // References a registered automation system, but with a base path name that is not registered
        private static final EntityLink UNKNOWN_BASE_PATH_LINK = EntityLink.builder()
                .identity(new NamedLink(URI.create("https://links.example/rel/automation"), "unknown-base-path"))
                .fallbackTemplate(automationTemplate("my-automation", "unknown",
                        "/documents/%{entity.id}"))
                .build();

        private static final Entity DOCUMENT = Entity.builder()
                .name(EntityName.of("document"))
                .table(TableName.of("document"))
                .pathSegment(PathSegmentName.of("documents"))
                .linkName(LinkName.of("document"))
                .primaryKey(DOCUMENT_ID)
                .attribute(DOCUMENT_CATEGORY)
                .attribute(DOCUMENT_ATTACHMENT)
                .link(CATEGORY_LINK)
                .link(PREVIEW_LINK)
                .link(ATTACHMENT_SCAN_LINK)
                .link(AUTHOR_LINK)
                .link(AUTOMATION_LINK)
                .link(UNKNOWN_AUTOMATION_LINK)
                .link(UNKNOWN_BASE_PATH_LINK)
                .build();

        private static final ManyToOneRelation DOCUMENT_AUTHOR = ManyToOneRelation.builder()
                .sourceEndPoint(RelationEndPoint.builder()
                        .entity(DOCUMENT.getName())
                        .name(RelationName.of("author"))
                        .pathSegment(PathSegmentName.of("author"))
                        .linkName(LinkName.of("author"))
                        .build())
                .targetEndPoint(RelationEndPoint.builder()
                        .entity(DOCUMENT.getName())
                        .build())
                .targetReference(ColumnName.of("author_id"))
                .build();

        private static final Application DOCUMENT_APPLICATION = Application.builder()
                .name(ApplicationName.of("document-application"))
                .entity(DOCUMENT)
                .relation(DOCUMENT_AUTHOR)
                .build();

        private static UriTemplateDefinition template(String template) {
            return new SimpleUriTemplateDefinition(
                    new ParameterizedUriTemplateParser<>(EnumSet.allOf(EntityLinkSubstitutionVariables.class))
                            .parseUnchecked(template)
            );
        }

        private static UriTemplateDefinition automationTemplate(String automationSystem, String basePathName,
                String template) {
            return new AutomationUriTemplateDefinition(
                    automationSystem,
                    basePathName,
                    new ParameterizedUriTemplateParser<>(EnumSet.allOf(EntityLinkSubstitutionVariables.class))
                            .parseUnchecked(template)
            );
        }

        private void setupDocumentQuery(String categoryValue) {
            Mockito.when(queryEngine.findById(Mockito.any(), Mockito.any(), Mockito.any())).then(args -> {
                var request = args.getArgument(1, EntityRequest.class);
                return Optional.of(new EntityData(
                        EntityIdentity.forEntity(request.getEntityName(), request.getEntityId()),
                        List.of(new SimpleAttributeData<>(DOCUMENT_CATEGORY.getName(), categoryValue))
                ));
            });
        }

        private String encodedUriProviderLink(EntityId entityId, String suffix) {
            return URLEncoder.encode(
                    "http://localhost/document/" + entityId.getValue() + suffix,
                    StandardCharsets.UTF_8
            );
        }

        @Test
        void findById_expandsEntityLinks() {
            var entityId = EntityId.of(UUID.randomUUID());
            setupDocumentQuery("contracts");

            var result = datamodelApi.findById(DOCUMENT_APPLICATION,
                            EntityRequest.forEntity(DOCUMENT.getName(), entityId), AuthorizationContext.allowAll())
                    .orElseThrow();

            assertThat(result.getLinks()).containsExactlyInAnyOrder(
                    new EntityLinkData(
                            CATEGORY_LINK.getIdentity(),
                            URI.create("https://links.example/profile/category"),
                            "https://categories.example/document?name=category&value=contracts&id=" + entityId.getValue()
                    ),
                    new EntityLinkData(
                            PREVIEW_LINK.getIdentity(),
                            null,
                            "https://preview.example/render?src=" + encodedUriProviderLink(entityId, "")
                    ),
                    new EntityLinkData(
                            ATTACHMENT_SCAN_LINK.getIdentity(),
                            null,
                            "https://scanner.example/scan?content=" + encodedUriProviderLink(entityId, "/attachment")
                    ),
                    new EntityLinkData(
                            AUTHOR_LINK.getIdentity(),
                            null,
                            "https://people.example/info?me=" + encodedUriProviderLink(entityId, "/author")
                                    + "&relation=author"
                    ),
                    // The registered automation base url is prepended to the expanded template.
                    // UNKNOWN_AUTOMATION_LINK and UNKNOWN_BASE_PATH_LINK reference an unregistered
                    // automation system or base path name, so they are not rendered.
                    new EntityLinkData(
                            AUTOMATION_LINK.getIdentity(),
                            null,
                            "https://automation.example/my-automation/documents/" + entityId.getValue()
                                    + "?app=my-application-id"
                    )
            );
        }

        @Test
        void findById_omitsLinkReferencingOwnerValueWithoutData() {
            var entityId = EntityId.of(UUID.randomUUID());
            setupDocumentQuery(null);

            var result = datamodelApi.findById(DOCUMENT_APPLICATION,
                            EntityRequest.forEntity(DOCUMENT.getName(), entityId), AuthorizationContext.allowAll())
                    .orElseThrow();

            // The category link references %{owner.value}, which has no data, so it is not rendered.
            // Links that don't use %{owner.value} are still rendered
            assertThat(result.getLinks())
                    .extracting(EntityLinkData::getIdentity)
                    .containsExactlyInAnyOrder(
                            PREVIEW_LINK.getIdentity(),
                            ATTACHMENT_SCAN_LINK.getIdentity(),
                            AUTHOR_LINK.getIdentity(),
                            AUTOMATION_LINK.getIdentity()
                    );
        }

        @Test
        void create_expandsEntityLinks() throws InvalidPropertyDataException {
            var entityId = EntityId.of(UUID.randomUUID());
            Mockito.when(queryEngine.create(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                    .thenReturn(EntityData.builder()
                            .name(DOCUMENT.getName())
                            .id(entityId)
                            .attribute(new SimpleAttributeData<>(DOCUMENT_CATEGORY.getName(), "contracts"))
                            .build());

            var result = datamodelApi.create(DOCUMENT_APPLICATION, DOCUMENT.getName(),
                    MapRequestInputData.fromMap(Map.of("category", "contracts")),
                    AuthorizationContext.allowAll());

            assertThat(result.getLinks())
                    .extracting(EntityLinkData::getIdentity)
                    .containsExactlyInAnyOrder(
                            CATEGORY_LINK.getIdentity(),
                            PREVIEW_LINK.getIdentity(),
                            ATTACHMENT_SCAN_LINK.getIdentity(),
                            AUTHOR_LINK.getIdentity(),
                            AUTOMATION_LINK.getIdentity()
                    );
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
            Mockito.when(queryEngine.delete(Mockito.any(), deleteArg.capture(), Mockito.any(), Mockito.any()))
                    .thenReturn(Optional.of(data));

            datamodelApi.deleteEntity(APPLICATION, EntityRequest.forEntity(invoice, id), AuthorizationContext.allowAll());
            assertEquals(invoice, deleteArg.getValue().getEntityName());
            assertEquals(id, deleteArg.getValue().getEntityId());
        }

        @Test
        void deleteNonExistent() {
            EntityId id = EntityId.of(UUID.randomUUID());
            EntityName invoice = EntityName.of("invoice");

            Mockito.when(queryEngine.delete(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    datamodelApi.deleteEntity(APPLICATION, EntityRequest.forEntity(invoice, id),
                            AuthorizationContext.allowAll())
            ).isInstanceOf(EntityIdNotFoundException.class);

        }
    }
}
