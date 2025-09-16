package com.contentgrid.appserver.rest.property;

import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.core.StringContains.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contentgrid.appserver.application.model.Constraint.AllowedValuesConstraint;
import com.contentgrid.appserver.application.model.Constraint.RequiredConstraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.relations.flags.RequiredEndpointFlag;
import com.contentgrid.appserver.domain.DatamodelApi;
import com.contentgrid.appserver.domain.authorization.PermissionPredicate;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.domain.values.RelationRequest;
import com.contentgrid.appserver.domain.values.version.ExactlyVersion;
import com.contentgrid.appserver.query.engine.api.TableCreator;
import com.contentgrid.appserver.registry.ApplicationResolver;
import com.contentgrid.appserver.registry.SingleApplicationResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.Arguments.ArgumentSet;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;

/**
 * Test class for both {@link XToOneRelationRestController} and {@link XToManyRelationRestController}.
 */
@SpringBootTest(properties = "contentgrid.thunx.abac.source=none")
@AutoConfigureMockMvc(printOnlyOnFailure = false)
class RelationRestControllerTest {

    private static final EntityId PERSON_ID = EntityId.of(UUID.randomUUID());
    private static final EntityId INVOICE_ID = EntityId.of(UUID.randomUUID());

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DatamodelApi datamodelApi;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TableCreator tableCreator;

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        public ApplicationResolver testApplicationResolver() {
            return new SingleApplicationResolver(APPLICATION);
        }

                }

    @BeforeEach
    void setup() {
        tableCreator.createTables(APPLICATION);
    }

    @AfterEach
    void teardown() {
        tableCreator.dropTables(APPLICATION);
    }

    private EntityIdentity createEntity(Entity entity) throws Exception {
        var params = new LinkedMultiValueMap<String, String>();

        for (var attribute : entity.getAttributes()) {
            if (attribute instanceof SimpleAttribute sa && sa.hasConstraint(RequiredConstraint.class)) {
                params.add(sa.getName().getValue(), switch (sa.getType()) {
                    case LONG, DOUBLE -> "123";
                    case BOOLEAN -> "true";
                    case DATETIME -> Instant.now().toString();
                    case UUID, TEXT -> UUID.randomUUID().toString();
                });
                sa.getConstraint(AllowedValuesConstraint.class).ifPresent(allowedValues -> {
                    params.set(sa.getName().getValue(), allowedValues.getValues().getFirst());
                });
            }

        }

        for(var relation: APPLICATION.getRelationsForSourceEntity(entity)) {
            if(relation.getSourceEndPoint().hasFlag(RequiredEndpointFlag.class)) {
                var relatedEntity = APPLICATION.getEntityByName(relation.getTargetEndPoint().getEntity()).orElseThrow();
                var relationEntity = createEntity(relatedEntity);
                params.add(relation.getSourceEndPoint().getName().getValue(), "http://localhost/%s/%s".formatted(
                        relatedEntity.getPathSegment(),
                        relationEntity.getEntityId()
                ));
            }
        }

        var response = mockMvc.perform(post("/{entity}", entity.getPathSegment())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .params(params)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse();

        var id = objectMapper.readTree(response.getContentAsByteArray()).get("id").asText();
        return EntityIdentity.forEntity(
                entity.getName(),
                EntityId.of(UUID.fromString(id))
        );
    }

    @Nested
    class ValidInput {

        static Stream<ArgumentSet> toOneRelations() {
            return Stream.of(
                    Arguments.argumentSet("one-to-one", INVOICE_PREVIOUS),
                    Arguments.argumentSet("many-to-one", PERSON_CHILDREN.inverse())
            );
        }

        interface ETagHandler {
            Map.Entry<String, String> prepareETag(DatamodelApi api, RelationRequest relation, EntityIdentity targetIdentity);
        }

        private static final ETagHandler EMPTY_RELATION = (api,rel, target) -> {
            api.deleteRelation(APPLICATION, rel, PermissionPredicate.allowAll());
            return Map.entry("If-None-Match", "*");
        };

        private static final ETagHandler SET_RELATION = (api, rel, target) -> {
            api.setRelation(APPLICATION, rel, target.getEntityId(), PermissionPredicate.allowAll());
            var result = api.findRelationTarget(APPLICATION, rel, PermissionPredicate.allowAll()).orElseThrow();

            var version = (ExactlyVersion)result.getRelationIdentity().getVersion();

            return Map.entry("If-Match", "\"%s\"".formatted(version.getVersion()));
        };

        private static final ETagHandler FAILING_EMPTY_RELATION = (api, rel, target) -> {
            var setRel = SET_RELATION.prepareETag(api, rel, target);
            EMPTY_RELATION.prepareETag(api, rel, target);
            return setRel;
        };

        private static final ETagHandler FAILING_SET_RELATION_EMPTY = (api, rel, target) -> {
            var emptyRel = EMPTY_RELATION.prepareETag(api, rel, target);
            SET_RELATION.prepareETag(api, rel, target);
            return emptyRel;
        };

        private static final ETagHandler FAILING_SET_RELATION_OTHER = (api, rel, target) -> {
            SET_RELATION.prepareETag(api, rel, target);
            return Map.entry("If-Match", "\"non-matching\"");
        };

        static Stream<ArgumentSet> eTagHandlers() {
            return Stream.of(
                    Arguments.argumentSet("If-None-Match for non-existing", EMPTY_RELATION, true),
                    Arguments.argumentSet("If-None-Match for existing", FAILING_EMPTY_RELATION, false),
                    Arguments.argumentSet("If-Match for matching existing", SET_RELATION, true),
                    Arguments.argumentSet("If-Match for non-existing", FAILING_SET_RELATION_EMPTY, false),
                    Arguments.argumentSet("If-Match for non-matching existing", FAILING_SET_RELATION_OTHER, false)
            );
        }

        static Stream<Arguments> toOneRelations_eTag() {
            return toOneRelations()
                    .flatMap(toOneRel -> eTagHandlers().map(eTagHandler -> {
                        var args = Stream.concat(
                                Stream.of(toOneRel.get()),
                                Stream.of(eTagHandler.get())
                        ).toArray();
                        return Arguments.argumentSet(toOneRel.getName()+" "+eTagHandler.getName(), args);
                    }));
        }

        static Stream<Arguments> toManyRelations() {
            return Stream.of(
                    Arguments.argumentSet("one-to-many", PERSON_CHILDREN, "parent"),
                    Arguments.argumentSet("many-to-many", INVOICE_PRODUCTS.inverse(), "products"),
                    Arguments.argumentSet("many-to-many (uni-directional)", PERSON_FRIENDS, "_internal_person__friends")
            );
        }

        @ParameterizedTest
        @MethodSource("toOneRelations")
        void getToOneRelation(Relation relation) throws Exception {
            var sourceEntity = APPLICATION.getEntityByName(relation.getSourceEndPoint().getEntity()).orElseThrow();
            var targetEntity = APPLICATION.getEntityByName(relation.getTargetEndPoint().getEntity()).orElseThrow();
            var sourceEntityIdentity = createEntity(sourceEntity);
            var targetEntityIdentity = createEntity(targetEntity);

            // First create the relation
            datamodelApi.setRelation(APPLICATION, RelationRequest.forRelation(
                    relation.getSourceEndPoint().getEntity(),
                    sourceEntityIdentity.getEntityId(),
                    relation.getSourceEndPoint().getName()
            ), targetEntityIdentity.getEntityId(), PermissionPredicate.allowAll());

            // Then check if the redirect is correct
            mockMvc.perform(get("/{entity}/{sourceId}/{relation}", sourceEntity.getPathSegment(), sourceEntityIdentity.getEntityId(), relation.getSourceEndPoint().getPathSegment()))
                    .andExpect(status().isFound())
                    .andExpect(header().exists(HttpHeaders.ETAG))
                    .andExpect(header().string(HttpHeaders.LOCATION, "http://localhost/%s/%s".formatted(targetEntity.getPathSegment(), targetEntityIdentity.getEntityId())));
        }

        @ParameterizedTest
        @MethodSource("toManyRelations")
        void getToManyRelation(Relation relation, String queryParam) throws Exception {
            var sourceEntity = APPLICATION.getEntityByName(relation.getSourceEndPoint().getEntity()).orElseThrow();
            var targetEntity = APPLICATION.getEntityByName(relation.getTargetEndPoint().getEntity()).orElseThrow();
            var sourceEntityIdentity = createEntity(sourceEntity);

            mockMvc.perform(get("/{entity}/{sourceId}/{relation}", sourceEntity.getPathSegment(), sourceEntityIdentity.getEntityId(), relation.getSourceEndPoint().getPathSegment()))
                    .andExpect(status().isFound())
                    .andExpect(header().doesNotExist(HttpHeaders.ETAG))
                    .andExpect(header().string(HttpHeaders.LOCATION,
                            "http://localhost/%s?%s=%s".formatted(targetEntity.getPathSegment(), queryParam, sourceEntityIdentity.getEntityId())));
        }

        @ParameterizedTest
        @MethodSource("toManyRelations")
        void getToManyRelationItem(Relation relation, String queryParam) throws Exception {
            var sourceEntity = APPLICATION.getEntityByName(relation.getSourceEndPoint().getEntity()).orElseThrow();
            var targetEntity = APPLICATION.getEntityByName(relation.getTargetEndPoint().getEntity()).orElseThrow();
            var sourceEntityIdentity = createEntity(sourceEntity);
            var targetEntityIdentity = createEntity(targetEntity);

            datamodelApi.addRelationItems(APPLICATION, relation, sourceEntityIdentity.getEntityId(), Set.of(targetEntityIdentity.getEntityId()), PermissionPredicate.allowAll());

            mockMvc.perform(get("/{entity}/{sourceId}/{relation}/{targetId}", sourceEntity.getPathSegment(), sourceEntityIdentity.getEntityId(), relation.getSourceEndPoint().getPathSegment(), targetEntityIdentity.getEntityId()))
                    .andExpect(status().isFound())
                    .andExpect(header().doesNotExist(HttpHeaders.ETAG))
                    .andExpect(
                            header().string(HttpHeaders.LOCATION, "http://localhost/%s/%s".formatted(targetEntity.getPathSegment(), targetEntityIdentity.getEntityId())));
        }

        @ParameterizedTest
        @MethodSource("toOneRelations")
        void setToOneRelation(Relation relation) throws Exception {
            var sourceEntity = APPLICATION.getEntityByName(relation.getSourceEndPoint().getEntity()).orElseThrow();
            var targetEntity = APPLICATION.getEntityByName(relation.getTargetEndPoint().getEntity()).orElseThrow();
            var sourceEntityIdentity = createEntity(sourceEntity);
            var targetEntityIdentity = createEntity(targetEntity);

            mockMvc.perform(put("/{entity}/{sourceId}/{relation}", sourceEntity.getPathSegment(), sourceEntityIdentity.getEntityId(), relation.getSourceEndPoint().getPathSegment())
                            .contentType("text/uri-list")
                            .content("http://localhost/%s/%s%n".formatted(targetEntity.getPathSegment(), targetEntityIdentity.getEntityId())))
                    .andExpect(status().isNoContent());

            assertThat(datamodelApi.hasRelationTarget(APPLICATION, relation, sourceEntityIdentity.getEntityId(), targetEntityIdentity.getEntityId(), PermissionPredicate.allowAll())).isTrue();
        }

        @ParameterizedTest
        @MethodSource("toOneRelations_eTag")
        void setToOneRelation_eTag(Relation relation, ETagHandler eTagHandler, boolean succeeds) throws Exception {
            var sourceEntity = APPLICATION.getEntityByName(relation.getSourceEndPoint().getEntity()).orElseThrow();
            var targetEntity = APPLICATION.getEntityByName(relation.getTargetEndPoint().getEntity()).orElseThrow();
            var sourceEntityIdentity = createEntity(sourceEntity);
            var targetEntityIdentity = createEntity(targetEntity);

            var relationRequest = RelationRequest.forRelation(
                    sourceEntityIdentity.getEntityName(),
                    sourceEntityIdentity.getEntityId(),
                    relation.getSourceEndPoint().getName()
            );

            var eTagHeader = eTagHandler.prepareETag(datamodelApi, relationRequest, targetEntityIdentity);

            mockMvc.perform(put("/{entity}/{sourceId}/{relation}", sourceEntity.getPathSegment(), sourceEntityIdentity.getEntityId(), relation.getSourceEndPoint().getPathSegment())
                            .contentType("text/uri-list")
                            .content("http://localhost/%s/%s%n".formatted(targetEntity.getPathSegment(), targetEntityIdentity.getEntityId()))
                            .header(eTagHeader.getKey(), eTagHeader.getValue())
                    )
                    .andExpect(succeeds?status().isNoContent():status().isPreconditionFailed());
        }

        @ParameterizedTest
        @MethodSource({"toOneRelations", "toManyRelations"})
        void clearRelation(Relation relation) throws Exception {
            var sourceEntity = APPLICATION.getEntityByName(relation.getSourceEndPoint().getEntity()).orElseThrow();
            var targetEntity = APPLICATION.getEntityByName(relation.getTargetEndPoint().getEntity()).orElseThrow();
            var sourceEntityIdentity = createEntity(sourceEntity);
            var targetEntityIdentity = createEntity(targetEntity);

            var createMethod = relation instanceof OneToManyRelation|| relation instanceof ManyToManyRelation?HttpMethod.POST:HttpMethod.PUT;
            // First create the relation
            mockMvc.perform(request(createMethod, "/{entity}/{sourceId}/{relation}", sourceEntity.getPathSegment(), sourceEntityIdentity.getEntityId(), relation.getSourceEndPoint().getPathSegment())
                            .contentType("text/uri-list")
                    .content("http://localhost/%s/%s%n".formatted(targetEntity.getPathSegment(), targetEntityIdentity.getEntityId()))
            ).andExpect(status().is2xxSuccessful());

            assertThat(datamodelApi.hasRelationTarget(APPLICATION, relation, sourceEntityIdentity.getEntityId(), targetEntityIdentity.getEntityId(), PermissionPredicate.allowAll())).isTrue();


            // Then delete it again
            mockMvc.perform(delete("/{entity}/{sourceId}/{relation}", sourceEntity.getPathSegment(), sourceEntityIdentity.getEntityId(), relation.getSourceEndPoint().getPathSegment()))
                    .andExpect(status().isNoContent());

            assertThat(datamodelApi.hasRelationTarget(APPLICATION, relation, sourceEntityIdentity.getEntityId(), targetEntityIdentity.getEntityId(), PermissionPredicate.allowAll())).isFalse();
        }

        @ParameterizedTest
        @MethodSource("toOneRelations_eTag")
        void clearToOneRelation_eTag(Relation relation, ETagHandler eTagHandler, boolean succeeds) throws Exception {
            var sourceEntity = APPLICATION.getEntityByName(relation.getSourceEndPoint().getEntity()).orElseThrow();
            var targetEntity = APPLICATION.getEntityByName(relation.getTargetEndPoint().getEntity()).orElseThrow();
            var sourceEntityIdentity = createEntity(sourceEntity);
            var targetEntityIdentity = createEntity(targetEntity);

            var relationRequest = RelationRequest.forRelation(
                    sourceEntityIdentity.getEntityName(),
                    sourceEntityIdentity.getEntityId(),
                    relation.getSourceEndPoint().getName()
            );

            var eTagHeader = eTagHandler.prepareETag(datamodelApi, relationRequest, targetEntityIdentity);

            mockMvc.perform(delete("/{entity}/{sourceId}/{relation}", sourceEntity.getPathSegment(), sourceEntityIdentity.getEntityId(), relation.getSourceEndPoint().getPathSegment())
                            .header(eTagHeader.getKey(), eTagHeader.getValue())
                    )
                    .andExpect(succeeds?status().isNoContent():status().isPreconditionFailed());
        }

        @ParameterizedTest
        @MethodSource("toManyRelations")
        void addToManyRelationItems(Relation relation) throws Exception {
            var sourceEntity = APPLICATION.getEntityByName(relation.getSourceEndPoint().getEntity()).orElseThrow();
            var targetEntity = APPLICATION.getEntityByName(relation.getTargetEndPoint().getEntity()).orElseThrow();
            var sourceEntityIdentity = createEntity(sourceEntity);
            var targetEntityIdentity1 = createEntity(targetEntity);
            var targetEntityIdentity2 = createEntity(targetEntity);

            mockMvc.perform(post("/{entity}/{sourceId}/{relation}", sourceEntity.getPathSegment(), sourceEntityIdentity.getEntityId(), relation.getSourceEndPoint().getPathSegment())
                            .contentType("text/uri-list")
                            .content("http://localhost/%s/%s%nhttp://localhost/%1$s/%s%n".formatted(targetEntity.getPathSegment(),
                                    targetEntityIdentity1.getEntityId(), targetEntityIdentity2.getEntityId()))
                    )
                    .andExpect(status().isNoContent());

            assertThat(datamodelApi.hasRelationTarget(APPLICATION, relation, sourceEntityIdentity.getEntityId(), targetEntityIdentity1.getEntityId(), PermissionPredicate.allowAll())).isTrue();
            assertThat(datamodelApi.hasRelationTarget(APPLICATION, relation, sourceEntityIdentity.getEntityId(), targetEntityIdentity2.getEntityId(), PermissionPredicate.allowAll())).isTrue();
        }

        @ParameterizedTest
        @MethodSource("toManyRelations")
        void removeToManyRelationItem(Relation relation) throws Exception {
            var sourceEntity = APPLICATION.getEntityByName(relation.getSourceEndPoint().getEntity()).orElseThrow();
            var targetEntity = APPLICATION.getEntityByName(relation.getTargetEndPoint().getEntity()).orElseThrow();
            var sourceEntityIdentity = createEntity(sourceEntity);
            var targetEntityIdentity1 = createEntity(targetEntity);
            var targetEntityIdentity2 = createEntity(targetEntity);

            datamodelApi.addRelationItems(APPLICATION, relation, sourceEntityIdentity.getEntityId(), Set.of(targetEntityIdentity1.getEntityId(), targetEntityIdentity2.getEntityId()), PermissionPredicate.allowAll());


            mockMvc.perform(delete("/{entity}/{sourceId}/{relation}/{id}", sourceEntity.getPathSegment(), sourceEntityIdentity.getEntityId(), relation.getSourceEndPoint().getPathSegment(), targetEntityIdentity2.getEntityId()))
                    .andExpect(status().isNoContent());

            assertThat(datamodelApi.hasRelationTarget(APPLICATION, relation, sourceEntityIdentity.getEntityId(), targetEntityIdentity1.getEntityId(), PermissionPredicate.allowAll())).isTrue();
            assertThat(datamodelApi.hasRelationTarget(APPLICATION, relation, sourceEntityIdentity.getEntityId(), targetEntityIdentity2.getEntityId(), PermissionPredicate.allowAll())).isFalse();
        }
    }

    @Nested
    class InvalidInput {

        static Stream<String> invalidUrls() {
            var targetId = EntityId.of(UUID.randomUUID());
            return Stream.of(
                    "http://localhost/persons/%s%n".formatted(targetId), // person instead of invoice
                    "http://localhost/invoices%n".formatted(), // collection url
                    "http://localhost/invoices/%s/next-invoice%n".formatted(targetId), // relation url
                    "}%s%n".formatted(targetId) // illegal url
            );
        }

        static Stream<String> invalidContentType() {
            return Stream.of(
                    MediaType.MULTIPART_FORM_DATA_VALUE,
                    "{?/uri-list",
                    "text/<uri-list>"
                    // "text/uri-list; charset=<non-existing>" // MockMvc itself is unable to parse it
            );
        }

        @ParameterizedTest
        @CsvSource({
                "/invoices/01234567-89ab-cdef-0123-456789abcdef/non-existing", // non-existing relation
                "/non-existing/01234567-89ab-cdef-0123-456789abcdef/previous-invoice", // non-existing entity
                "/invoices/01234567-89ab-cdef-0123-456789abcdef/non-existing/01234567-89ab-cdef-0123-456789abcdef", // non-existing relation
        })
        void followRelationInvalidUrl(String url) throws Exception {
            mockMvc.perform(get(url))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void setRelationNoData() throws Exception {
            var invoice = createEntity(INVOICE);
            mockMvc.perform(put("/invoices/{sourceId}/previous-invoice", invoice.getEntityId())
                            .contentType("text/uri-list")
                            .content("%n".formatted()))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void setRelationMissingContent() throws Exception {
            var invoice = createEntity(INVOICE);
            mockMvc.perform(put("/invoices/{sourceId}/previous-invoice", invoice.getEntityId()))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void setRelationTooManyData() throws Exception {
            var invoice = createEntity(INVOICE);
            var target1 = EntityId.of(UUID.randomUUID());
            var target2 = EntityId.of(UUID.randomUUID());

            mockMvc.perform(put("/invoices/{sourceId}/previous-invoice", invoice.getEntityId())
                            .contentType("text/uri-list")
                            .content("http://localhost/invoices/%s%nhttp://localhost/invoices/%s%n".formatted(target1, target2)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @ParameterizedTest
        @MethodSource("invalidUrls")
        void setRelationInvalidUrl(String url) throws Exception {
            var invoice = createEntity(INVOICE);
            mockMvc.perform(put("/invoices/{sourceId}/previous-invoice", invoice.getEntityId())
                            .contentType("text/uri-list")
                            .content(url))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @ParameterizedTest
        @MethodSource("invalidContentType")
        void setRelationInvalidMimeType(String contentType) throws Exception {
            var invoice = createEntity(INVOICE);
            var targetId = EntityId.of(UUID.randomUUID());
            mockMvc.perform(put("/invoices/{sourceId}/previous-invoice", invoice.getEntityId())
                            .contentType(contentType)
                            .content("http://localhost/invoices/%s%n".formatted(targetId)))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value(startsWith("https://contentgrid.cloud/problems/invalid-media-type")));
        }

        @Test
        void addRelationNoData() throws Exception {
            var person = createEntity(PERSON);
            mockMvc.perform(post("/persons/{sourceId}/invoices", person.getEntityId())
                            .contentType("text/uri-list")
                            .content("%n".formatted()))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void addRelationMissingContent() throws Exception {
            var person = createEntity(PERSON);
            mockMvc.perform(post("/persons/{sourceId}/invoices", person.getEntityId()))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @ParameterizedTest
        @MethodSource("invalidContentType")
        void addRelationInvalidMimeType(String contentType) throws Exception {
            var person = createEntity(PERSON);
            var targetId = EntityId.of(UUID.randomUUID());
            mockMvc.perform(post("/persons/{sourceId}/invoices", person.getEntityId())
                            .contentType(contentType)
                            .content("http://localhost/invoices/%s%n".formatted(targetId)))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value(startsWith("https://contentgrid.cloud/problems/invalid-media-type")));
        }

        @ParameterizedTest
        @MethodSource("invalidUrls")
        void addRelationInvalidUrl(String url) throws Exception {
            var person = createEntity(PERSON);
            mockMvc.perform(post("/persons/{sourceId}/invoices", person.getEntityId())
                            .contentType("text/uri-list")
                            .content(url))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

        static Stream<Arguments> unsupportedMethod() {
            var targetId = EntityId.of(UUID.randomUUID());
            return Stream.of(
                    // property endpoint
                    Arguments.of(HttpMethod.PUT, "/persons/%s/invoices".formatted(PERSON_ID)),
                    Arguments.of(HttpMethod.POST, "/invoices/%s/previous-invoice".formatted(INVOICE_ID)),
                    Arguments.of(HttpMethod.PATCH, "/persons/%s/invoices".formatted(PERSON_ID)),
                    Arguments.of(HttpMethod.PATCH, "/invoices/%s/previous-invoice".formatted(INVOICE_ID)),
                    // property item endpoint
                    Arguments.of(HttpMethod.POST, "/persons/%s/invoices/%s".formatted(PERSON_ID, targetId)),
                    Arguments.of(HttpMethod.PUT, "/persons/%s/invoices/%s".formatted(PERSON_ID, targetId)),
                    Arguments.of(HttpMethod.PATCH, "/persons/%s/invoices/%s".formatted(PERSON_ID, targetId))
            );
        }

        @ParameterizedTest
        @MethodSource
        void unsupportedMethod(HttpMethod method, String url) throws Exception {
            var requestBuilder = request(method, url);
            if (Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH).contains(method)) {
                requestBuilder = requestBuilder.contentType("text/uri-list")
                        .content("http://localhost/invoices/%s%n".formatted(UUID.randomUUID()));
            }
            mockMvc.perform(requestBuilder)
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value(is("https://contentgrid.cloud/problems/method-not-allowed")))
                    .andExpect(header().exists(HttpHeaders.ALLOW))
                    .andExpect(header().string(HttpHeaders.ALLOW, containsString(HttpMethod.GET.name())))
                    .andExpect(header().string(HttpHeaders.ALLOW, not(containsString(method.name()))));
        }

        static Stream<Arguments> unsupportedUrl() {
            var targetId = EntityId.of(UUID.randomUUID());
            return Stream.of(
                    // property item endpoint of *-to-one relation
                    Arguments.of(HttpMethod.GET, "/invoices/%s/previous-invoice/%s".formatted(INVOICE_ID, targetId)),
                    Arguments.of(HttpMethod.POST, "/invoices/%s/previous-invoice/%s".formatted(INVOICE_ID, targetId)),
                    Arguments.of(HttpMethod.PUT, "/invoices/%s/previous-invoice/%s".formatted(INVOICE_ID, targetId)),
                    Arguments.of(HttpMethod.PATCH, "/invoices/%s/previous-invoice/%s".formatted(INVOICE_ID, targetId)),
                    Arguments.of(HttpMethod.DELETE, "/invoices/%s/previous-invoice/%s".formatted(INVOICE_ID, targetId))
            );
        }

        @ParameterizedTest
        @MethodSource
        void unsupportedUrl(HttpMethod method, String url) throws Exception {
            var requestBuilder = request(method, url);
            if (Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH).contains(method)) {
                requestBuilder = requestBuilder.contentType("text/uri-list")
                        .content("http://localhost/invoices/%s%n".formatted(UUID.randomUUID()));
            }
            mockMvc.perform(requestBuilder)
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

    }

    @Nested
    class DatabaseFailures {

        @Test
        void followToOneRelationSourceIdNotFound() throws Exception {
            mockMvc.perform(get("/invoices/{sourceId}/previous-invoice", INVOICE_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void followToOneRelationTargetIdNotFound() throws Exception {
            var invoice = createEntity(INVOICE);
            mockMvc.perform(get("/invoices/{sourceId}/previous-invoice", invoice.getEntityId()))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void followToManyRelationSourceIdNotFound() throws Exception {
            mockMvc.perform(get("/persons/{sourceId}/invoices", PERSON_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void followToManyRelationItemSourceIdOrTargetIdNotFound() throws Exception {
            var invoice = createEntity(INVOICE);
            var person = createEntity(PERSON);

            mockMvc.perform(get("/persons/{sourceId}/invoices/{targetId}", person.getEntityId(), invoice.getEntityId()))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void setRelationEntityIdNotFound() throws Exception {
            var invoice = createEntity(INVOICE);

            mockMvc.perform(put("/invoices/{sourceId}/previous-invoice", invoice.getEntityId())
                            .contentType("text/uri-list")
                            .content("http://localhost/invoices/%s%n".formatted(UUID.randomUUID())))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void setRelationBlindOverwrite() throws Exception {
            var invoice1 = createEntity(INVOICE);
            var invoice2 = createEntity(INVOICE);
            var invoice3 = createEntity(INVOICE);

            // link invoice1 -> invoice2
            mockMvc.perform(put("/invoices/{id}/previous-invoice", invoice1.getEntityId())
                            .contentType("text/uri-list")
                            .content("http://localhost/invoices/%s%n".formatted(invoice2.getEntityId()))
                    )
                    .andExpect(status().is2xxSuccessful());

            // try to link invoice3 -> invoice2
            mockMvc.perform(put("/invoices/{id}/previous-invoice", invoice3.getEntityId())
                            .contentType("text/uri-list")
                            .content("http://localhost/invoices/%s%n".formatted(invoice2.getEntityId()))
                    )
                    .andExpect(status().isConflict())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value("https://contentgrid.cloud/problems/integrity/relation-overwrite"))
                    .andExpect(jsonPath("$.affected-relation").value("http://localhost/invoices/%s/next-invoice".formatted(invoice2.getEntityId())))
                    .andExpect(jsonPath("$.existing-item").value("http://localhost/invoices/%s".formatted(invoice1.getEntityId())));
        }

        @Test
        void addRelationEntityIdNotFound() throws Exception {
            var invoice1 = createEntity(INVOICE);
            var invoice2 = createEntity(INVOICE);

            mockMvc.perform(post("/persons/{sourceId}/invoices", UUID.randomUUID())
                            .contentType("text/uri-list")
                            .content("http://localhost/invoices/%s%nhttp://localhost/invoices/%s%n".formatted(invoice1.getEntityId(),
                                    invoice2.getEntityId())))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void addRelationForeignKeyConstraintViolation() throws Exception {
            var person = createEntity(PERSON);
            var invoice1 = EntityId.of(UUID.randomUUID());
            var invoice2 = EntityId.of(UUID.randomUUID());

            mockMvc.perform(post("/persons/{sourceId}/invoices", person.getEntityId())
                            .contentType("text/uri-list")
                            .content("http://localhost/invoices/%s%nhttp://localhost/invoices/%s%n".formatted(invoice1,
                                    invoice2)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void clearRelationEntityIdNotFound() throws Exception {
            mockMvc.perform(delete("/invoices/{sourceId}/previous-invoice", UUID.randomUUID()))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void clearRelationForeignKeyRequired() throws Exception {
            var invoice = createEntity(INVOICE);

            mockMvc.perform(delete("/invoices/{sourceId}/customer", invoice.getEntityId()))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void removeRelationDataEntityIdNotFound() throws Exception {
            var person = createEntity(PERSON);
            var invoice = createEntity(INVOICE);

            mockMvc.perform(delete("/persons/{sourceId}/invoices/{targetId}", person.getEntityId(), invoice.getEntityId()))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void removeRelationDataForeignKeyRequired() throws Exception {
            var person = createEntity(PERSON);
            var invoice = createEntity(INVOICE);

            datamodelApi.setRelation(APPLICATION, RelationRequest.forRelation(INVOICE.getName(), invoice.getEntityId(), INVOICE_CUSTOMER.getSourceEndPoint().getName()), person.getEntityId(), PermissionPredicate.allowAll());

            mockMvc.perform(delete("/persons/{sourceId}/invoices/{targetId}", person.getEntityId(), invoice.getEntityId()))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

    }

}
