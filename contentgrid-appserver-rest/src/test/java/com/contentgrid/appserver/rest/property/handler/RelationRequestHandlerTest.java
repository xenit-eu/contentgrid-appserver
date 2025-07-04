package com.contentgrid.appserver.rest.property.handler;

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

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.domain.DatamodelApi;
import com.contentgrid.appserver.query.engine.api.TableCreator;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.query.engine.api.data.XToManyRelationData;
import com.contentgrid.appserver.query.engine.api.data.XToOneRelationData;
import com.contentgrid.appserver.query.engine.api.exception.ConstraintViolationException;
import com.contentgrid.appserver.query.engine.api.exception.EntityNotFoundException;
import com.contentgrid.appserver.registry.ApplicationResolver;
import com.contentgrid.appserver.registry.SingleApplicationResolver;
import com.contentgrid.appserver.rest.TestApplication;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Test class for both {@link XToOneRelationRequestHandler} and {@link XToManyRelationRequestHandler}.
 */
@SpringBootTest
@AutoConfigureMockMvc(printOnlyOnFailure = false)
class RelationRequestHandlerTest {

    private static final EntityId PERSON_ID = EntityId.of(UUID.randomUUID());
    private static final EntityId INVOICE_ID = EntityId.of(UUID.randomUUID());
    private static final EntityId PRODUCT_ID = EntityId.of(UUID.randomUUID());

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private final DatamodelApi datamodelApi = Mockito.mock(DatamodelApi.class);

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        public ApplicationResolver testApplicationResolver() {
            return new SingleApplicationResolver(TestApplication.APPLICATION);
        }

        @Bean
        @Primary
        public TableCreator noopTableCreator() {
            return new TableCreator() {
                @Override
                public void createTables(Application application) {
                    // avoid creating tables, we don't need them
                }

                @Override
                public void dropTables(Application application) {
                    // no tables were created
                }
            };
        }
    }

    @AfterEach
    void resetMocks() {
        Mockito.reset(datamodelApi);
    }

    @Nested
    class ValidInput {

        @Test
        void followOneToOneRelation() throws Exception {
            var targetId = EntityId.of(UUID.randomUUID());

            Mockito.doReturn(Optional.of(targetId)).when(datamodelApi)
                    .findRelationTarget(TestApplication.APPLICATION, TestApplication.INVOICE_PREVIOUS, INVOICE_ID);

            mockMvc.perform(get("/invoices/{sourceId}/previous-invoice", INVOICE_ID))
                    .andExpect(status().isFound())
                    .andExpect(
                            header().string(HttpHeaders.LOCATION, "http://localhost/invoices/%s".formatted(targetId)));

            Mockito.verify(datamodelApi)
                    .findRelationTarget(TestApplication.APPLICATION, TestApplication.INVOICE_PREVIOUS, INVOICE_ID);
        }

        @Test
        void followManyToOneRelation() throws Exception {
            var targetId = EntityId.of(UUID.randomUUID());

            Mockito.doReturn(Optional.of(targetId)).when(datamodelApi)
                    .findRelationTarget(TestApplication.APPLICATION, TestApplication.INVOICE_CUSTOMER, INVOICE_ID);

            mockMvc.perform(get("/invoices/{sourceId}/customer", INVOICE_ID))
                    .andExpect(status().isFound())
                    .andExpect(
                            header().string(HttpHeaders.LOCATION, "http://localhost/persons/%s".formatted(targetId)));

            Mockito.verify(datamodelApi)
                    .findRelationTarget(TestApplication.APPLICATION, TestApplication.INVOICE_CUSTOMER, INVOICE_ID);
        }

        @Test
        void followOneToManyRelation() throws Exception {
            Mockito.doReturn(Optional.of(EntityData.builder()
                            .name(TestApplication.PERSON.getName())
                            .id(PERSON_ID)
                            .build()))
                    .when(datamodelApi)
                    .findById(TestApplication.APPLICATION, TestApplication.PERSON, PERSON_ID);

            mockMvc.perform(get("/persons/{sourceId}/invoices", PERSON_ID))
                    .andExpect(status().isFound())
                    .andExpect(header().string(HttpHeaders.LOCATION,
                            "http://localhost/invoices?page=0&customer=%s".formatted(PERSON_ID)));

            Mockito.verify(datamodelApi)
                    .findById(TestApplication.APPLICATION, TestApplication.PERSON, PERSON_ID);
        }

        @Test
        void followManyToManyRelation() throws Exception {
            Mockito.doReturn(Optional.of(EntityData.builder()
                            .name(TestApplication.PRODUCT.getName())
                            .id(PRODUCT_ID)
                            .build()))
                    .when(datamodelApi)
                    .findById(TestApplication.APPLICATION, TestApplication.PRODUCT, PRODUCT_ID);

            mockMvc.perform(get("/products/{sourceId}/invoices", PRODUCT_ID))
                    .andExpect(status().isFound())
                    .andExpect(header().string(HttpHeaders.LOCATION,
                            "http://localhost/invoices?page=0&products=%s".formatted(PRODUCT_ID)));

            Mockito.verify(datamodelApi)
                    .findById(TestApplication.APPLICATION, TestApplication.PRODUCT, PRODUCT_ID);
        }

        @Test
        @Disabled("Following unidirectional *-to-many relations not implemented")
        void followUnidirectionalToManyRelation() throws Exception {
            Mockito.doReturn(Optional.of(EntityData.builder()
                    .name(TestApplication.PERSON.getName())
                    .id(PERSON_ID)
                    .build()))
                    .when(datamodelApi)
                    .findById(TestApplication.APPLICATION, TestApplication.PERSON, PERSON_ID);

            mockMvc.perform(get("/persons/{sourceId}/friends", PERSON_ID))
                    .andExpect(status().isFound())
                    .andExpect(header().string(HttpHeaders.LOCATION,
                            "http://localhost/persons?page=0&_internal_person__friends=%s".formatted(PERSON_ID))); // TODO: ACC-2149 change url

            Mockito.verify(datamodelApi)
                    .findById(TestApplication.APPLICATION, TestApplication.PERSON, PERSON_ID);
        }

        @Test
        void followOneToManyRelationItem() throws Exception {
            var targetId = EntityId.of(UUID.randomUUID());

            Mockito.doReturn(true).when(datamodelApi)
                    .hasRelationTarget(TestApplication.APPLICATION, TestApplication.PERSON_INVOICES, PERSON_ID, targetId);

            mockMvc.perform(get("/persons/{sourceId}/invoices/{targetId}", PERSON_ID, targetId))
                    .andExpect(status().isFound())
                    .andExpect(
                            header().string(HttpHeaders.LOCATION, "http://localhost/invoices/%s".formatted(targetId)));

            Mockito.verify(datamodelApi)
                    .hasRelationTarget(TestApplication.APPLICATION, TestApplication.PERSON_INVOICES, PERSON_ID, targetId);
        }

        @Test
        void followManyToManyRelationItem() throws Exception {
            var targetId = EntityId.of(UUID.randomUUID());

            Mockito.doReturn(true).when(datamodelApi)
                    .hasRelationTarget(TestApplication.APPLICATION, TestApplication.PRODUCT_INVOICES, PRODUCT_ID, targetId);

            mockMvc.perform(get("/products/{sourceId}/invoices/{targetId}", PRODUCT_ID, targetId))
                    .andExpect(status().isFound())
                    .andExpect(
                            header().string(HttpHeaders.LOCATION, "http://localhost/invoices/%s".formatted(targetId)));

            Mockito.verify(datamodelApi)
                    .hasRelationTarget(TestApplication.APPLICATION, TestApplication.PRODUCT_INVOICES, PRODUCT_ID, targetId);
        }

        @Test
        void setOneToOneRelation() throws Exception {
            var targetId = EntityId.of(UUID.randomUUID());

            mockMvc.perform(put("/invoices/{sourceId}/previous-invoice", INVOICE_ID)
                            .contentType("text/uri-list")
                            .content("http://localhost/invoices/%s%n".formatted(targetId)))
                    .andExpect(status().isNoContent());

            Mockito.verify(datamodelApi).setRelation(TestApplication.APPLICATION, XToOneRelationData.builder()
                    .entity(EntityName.of("invoice"))
                    .name(RelationName.of("previous_invoice"))
                    .ref(targetId)
                    .build(), INVOICE_ID);
        }

        @Test
        void setManyToOneRelation() throws Exception {
            var targetId = EntityId.of(UUID.randomUUID());

            mockMvc.perform(put("/invoices/{sourceId}/customer", INVOICE_ID)
                            .contentType("text/uri-list")
                            .content("http://localhost/persons/%s%n".formatted(targetId)))
                    .andExpect(status().isNoContent());

            Mockito.verify(datamodelApi).setRelation(TestApplication.APPLICATION, XToOneRelationData.builder()
                    .entity(EntityName.of("invoice"))
                    .name(RelationName.of("customer"))
                    .ref(targetId)
                    .build(), INVOICE_ID);
        }

        @Test
        void clearOneToOneRelation() throws Exception {
            mockMvc.perform(delete("/invoices/{sourceId}/previous-invoice", INVOICE_ID))
                    .andExpect(status().isNoContent());

            Mockito.verify(datamodelApi)
                    .deleteRelation(TestApplication.APPLICATION, TestApplication.INVOICE_PREVIOUS, INVOICE_ID);
        }

        @Test
        void clearManyToOneRelation() throws Exception {
            mockMvc.perform(delete("/invoices/{sourceId}/customer", INVOICE_ID))
                    .andExpect(status().isNoContent());

            Mockito.verify(datamodelApi)
                    .deleteRelation(TestApplication.APPLICATION, TestApplication.INVOICE_CUSTOMER, INVOICE_ID);
        }

        @Test
        void clearOneToManyRelation() throws Exception {
            mockMvc.perform(delete("/persons/{sourceId}/invoices", PERSON_ID))
                    .andExpect(status().isNoContent());

            Mockito.verify(datamodelApi)
                    .deleteRelation(TestApplication.APPLICATION, TestApplication.PERSON_INVOICES, PERSON_ID);
        }

        @Test
        void clearManyToManyRelation() throws Exception {
            mockMvc.perform(delete("/products/{sourceId}/invoices", PRODUCT_ID))
                    .andExpect(status().isNoContent());

            Mockito.verify(datamodelApi)
                    .deleteRelation(TestApplication.APPLICATION, TestApplication.PRODUCT_INVOICES, PRODUCT_ID);
        }

        @Test
        void addOneToManyRelationItems() throws Exception {
            var invoice1 = EntityId.of(UUID.randomUUID());
            var invoice2 = EntityId.of(UUID.randomUUID());

            mockMvc.perform(post("/persons/{sourceId}/invoices", PERSON_ID)
                            .contentType("text/uri-list")
                            .content("http://localhost/invoices/%s%nhttp://localhost/invoices/%s%n".formatted(invoice1,
                                    invoice2)))
                    .andExpect(status().isNoContent());

            Mockito.verify(datamodelApi).addRelationItems(TestApplication.APPLICATION, XToManyRelationData.builder()
                    .entity(EntityName.of("person"))
                    .name(RelationName.of("invoices"))
                    .ref(invoice1)
                    .ref(invoice2)
                    .build(), PERSON_ID);
        }

        @Test
        void addManyToManyRelationItems() throws Exception {
            var invoice1 = EntityId.of(UUID.randomUUID());
            var invoice2 = EntityId.of(UUID.randomUUID());

            mockMvc.perform(post("/products/{sourceId}/invoices", PRODUCT_ID)
                            .contentType("text/uri-list")
                            .content("http://localhost/invoices/%s%nhttp://localhost/invoices/%s%n".formatted(invoice1,
                                    invoice2)))
                    .andExpect(status().isNoContent());

            Mockito.verify(datamodelApi).addRelationItems(TestApplication.APPLICATION, XToManyRelationData.builder()
                    .entity(EntityName.of("product"))
                    .name(RelationName.of("invoices"))
                    .ref(invoice1)
                    .ref(invoice2)
                    .build(), PRODUCT_ID);
        }

        @Test
        void removeOneToManyRelationItem() throws Exception {
            mockMvc.perform(delete("/persons/{sourceId}/invoices/{targetId}", PERSON_ID, INVOICE_ID))
                    .andExpect(status().isNoContent());

            Mockito.verify(datamodelApi)
                    .removeRelationItem(TestApplication.APPLICATION, TestApplication.PERSON_INVOICES, PERSON_ID,
                            INVOICE_ID);
        }

        @Test
        void removeManyToManyRelationItem() throws Exception {
            mockMvc.perform(delete("/products/{sourceId}/invoices/{targetId}", PRODUCT_ID, INVOICE_ID))
                    .andExpect(status().isNoContent());

            Mockito.verify(datamodelApi)
                    .removeRelationItem(TestApplication.APPLICATION, TestApplication.PRODUCT_INVOICES, PRODUCT_ID,
                            INVOICE_ID);
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
            mockMvc.perform(put("/invoices/{sourceId}/previous-invoice", INVOICE_ID)
                            .contentType("text/uri-list")
                            .content("%n".formatted()))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void setRelationMissingContent() throws Exception {
            mockMvc.perform(put("/invoices/{sourceId}/previous-invoice", INVOICE_ID))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void setRelationTooManyData() throws Exception {
            var target1 = EntityId.of(UUID.randomUUID());
            var target2 = EntityId.of(UUID.randomUUID());

            mockMvc.perform(put("/invoices/{sourceId}/previous-invoice", INVOICE_ID)
                            .contentType("text/uri-list")
                            .content("http://localhost/invoices/%s%nhttp://localhost/invoices/%s%n".formatted(target1, target2)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @ParameterizedTest
        @MethodSource("invalidUrls")
        void setRelationInvalidUrl(String url) throws Exception {
            mockMvc.perform(put("/invoices/{sourceId}/previous-invoice", INVOICE_ID)
                            .contentType("text/uri-list")
                            .content(url))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @ParameterizedTest
        @MethodSource("invalidContentType")
        void setRelationInvalidMimeType(String contentType) throws Exception {
            var targetId = EntityId.of(UUID.randomUUID());
            mockMvc.perform(put("/invoices/{sourceId}/previous-invoice", INVOICE_ID)
                            .contentType(contentType)
                            .content("http://localhost/invoices/%s%n".formatted(targetId)))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value(startsWith("https://contentgrid.cloud/problems/invalid-media-type")));
        }

        @Test
        void addRelationNoData() throws Exception {
            mockMvc.perform(post("/persons/{sourceId}/invoices", PERSON_ID)
                            .contentType("text/uri-list")
                            .content("%n".formatted()))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void addRelationMissingContent() throws Exception {
            mockMvc.perform(post("/persons/{sourceId}/invoices", PERSON_ID))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @ParameterizedTest
        @MethodSource("invalidContentType")
        void addRelationInvalidMimeType(String contentType) throws Exception {
            var targetId = EntityId.of(UUID.randomUUID());
            mockMvc.perform(post("/persons/{sourceId}/invoices", PERSON_ID)
                            .contentType(contentType)
                            .content("http://localhost/invoices/%s%n".formatted(targetId)))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value(startsWith("https://contentgrid.cloud/problems/invalid-media-type")));
        }

        @ParameterizedTest
        @MethodSource("invalidUrls")
        void addRelationInvalidUrl(String url) throws Exception {
            mockMvc.perform(post("/persons/{sourceId}/invoices", PERSON_ID)
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

        private static final EntityNotFoundException ENTITY_ID_NOT_FOUND = new EntityNotFoundException("Entity id not found");
        private static final ConstraintViolationException FOREIGN_KEY_NOT_FOUND = new ConstraintViolationException("Foreign key not found");
        private static final ConstraintViolationException FOREIGN_KEY_REQUIRED = new ConstraintViolationException("Foreign key is required");

        @Test
        void followToOneRelationSourceIdNotFound() throws Exception {
            Mockito.doThrow(ENTITY_ID_NOT_FOUND).when(datamodelApi)
                    .findRelationTarget(TestApplication.APPLICATION, TestApplication.INVOICE_PREVIOUS, INVOICE_ID);

            mockMvc.perform(get("/invoices/{sourceId}/previous-invoice", INVOICE_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void followToOneRelationTargetIdNotFound() throws Exception {
            Mockito.doReturn(Optional.empty()).when(datamodelApi)
                    .findRelationTarget(TestApplication.APPLICATION, TestApplication.INVOICE_PREVIOUS, INVOICE_ID);

            mockMvc.perform(get("/invoices/{sourceId}/previous-invoice", INVOICE_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void followToManyRelationSourceIdNotFound() throws Exception {
            Mockito.doReturn(Optional.empty()).when(datamodelApi)
                    .findById(TestApplication.APPLICATION, TestApplication.PERSON, PERSON_ID);

            mockMvc.perform(get("/persons/{sourceId}/invoices", PERSON_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void followToManyRelationItemSourceIdOrTargetIdNotFound() throws Exception {
            var targetId = EntityId.of(UUID.randomUUID());

            // Returns false if sourceId or targetId does not exist, or if there is no relation between sourceId and targetId
            Mockito.doReturn(false).when(datamodelApi)
                    .hasRelationTarget(TestApplication.APPLICATION, TestApplication.PERSON_INVOICES, PERSON_ID, targetId);

            mockMvc.perform(get("/persons/{sourceId}/invoices/{targetId}", PERSON_ID, targetId))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void setRelationEntityIdNotFound() throws Exception {
            var targetId = EntityId.of(UUID.randomUUID());

            Mockito.doThrow(ENTITY_ID_NOT_FOUND).when(datamodelApi)
                    .setRelation(TestApplication.APPLICATION, XToOneRelationData.builder()
                            .entity(EntityName.of("invoice"))
                            .name(RelationName.of("previous_invoice"))
                            .ref(targetId)
                            .build(), INVOICE_ID);

            mockMvc.perform(put("/invoices/{sourceId}/previous-invoice", INVOICE_ID)
                            .contentType("text/uri-list")
                            .content("http://localhost/invoices/%s%n".formatted(targetId)))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void setRelationForeignKeyConstraintViolation() throws Exception {
            var targetId = EntityId.of(UUID.randomUUID());

            Mockito.doThrow(FOREIGN_KEY_NOT_FOUND).when(datamodelApi)
                    .setRelation(TestApplication.APPLICATION, XToOneRelationData.builder()
                            .entity(EntityName.of("invoice"))
                            .name(RelationName.of("previous_invoice"))
                            .ref(targetId)
                            .build(), INVOICE_ID);

            mockMvc.perform(put("/invoices/{sourceId}/previous-invoice", INVOICE_ID)
                            .contentType("text/uri-list")
                            .content("http://localhost/invoices/%s%n".formatted(targetId)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void addRelationEntityIdNotFound() throws Exception {
            var invoice1 = EntityId.of(UUID.randomUUID());
            var invoice2 = EntityId.of(UUID.randomUUID());

            Mockito.doThrow(ENTITY_ID_NOT_FOUND).when(datamodelApi)
                    .addRelationItems(TestApplication.APPLICATION, XToManyRelationData.builder()
                            .entity(EntityName.of("person"))
                            .name(RelationName.of("invoices"))
                            .ref(invoice1)
                            .ref(invoice2)
                            .build(), PERSON_ID);

            mockMvc.perform(post("/persons/{sourceId}/invoices", PERSON_ID)
                            .contentType("text/uri-list")
                            .content("http://localhost/invoices/%s%nhttp://localhost/invoices/%s%n".formatted(invoice1,
                                    invoice2)))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void addRelationForeignKeyConstraintViolation() throws Exception {
            var invoice1 = EntityId.of(UUID.randomUUID());
            var invoice2 = EntityId.of(UUID.randomUUID());

            Mockito.doThrow(FOREIGN_KEY_NOT_FOUND).when(datamodelApi)
                    .addRelationItems(TestApplication.APPLICATION, XToManyRelationData.builder()
                            .entity(EntityName.of("person"))
                            .name(RelationName.of("invoices"))
                            .ref(invoice1)
                            .ref(invoice2)
                            .build(), PERSON_ID);

            mockMvc.perform(post("/persons/{sourceId}/invoices", PERSON_ID)
                            .contentType("text/uri-list")
                            .content("http://localhost/invoices/%s%nhttp://localhost/invoices/%s%n".formatted(invoice1,
                                    invoice2)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void clearRelationEntityIdNotFound() throws Exception {
            Mockito.doThrow(ENTITY_ID_NOT_FOUND).when(datamodelApi)
                    .deleteRelation(TestApplication.APPLICATION, TestApplication.INVOICE_PREVIOUS, INVOICE_ID);

            mockMvc.perform(delete("/invoices/{sourceId}/previous-invoice", INVOICE_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void clearRelationForeignKeyRequired() throws Exception {
            Mockito.doThrow(FOREIGN_KEY_REQUIRED).when(datamodelApi)
                    .deleteRelation(TestApplication.APPLICATION, TestApplication.INVOICE_PREVIOUS, INVOICE_ID);

            mockMvc.perform(delete("/invoices/{sourceId}/previous-invoice", INVOICE_ID))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void removeRelationDataEntityIdNotFound() throws Exception {
            Mockito.doThrow(ENTITY_ID_NOT_FOUND).when(datamodelApi)
                    .removeRelationItem(TestApplication.APPLICATION, TestApplication.PERSON_INVOICES, PERSON_ID, INVOICE_ID);

            mockMvc.perform(delete("/persons/{sourceId}/invoices/{targetId}", PERSON_ID, INVOICE_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void removeRelationDataForeignKeyRequired() throws Exception {
            Mockito.doThrow(FOREIGN_KEY_REQUIRED).when(datamodelApi)
                    .removeRelationItem(TestApplication.APPLICATION, TestApplication.PERSON_INVOICES, PERSON_ID, INVOICE_ID);

            mockMvc.perform(delete("/persons/{sourceId}/invoices/{targetId}", PERSON_ID, INVOICE_ID))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }

    }

}