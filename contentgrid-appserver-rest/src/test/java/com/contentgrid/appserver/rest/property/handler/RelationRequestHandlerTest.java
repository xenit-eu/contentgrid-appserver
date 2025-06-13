package com.contentgrid.appserver.rest.property.handler;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
                            .name(EntityName.of("person"))
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
                            .name(EntityName.of("product"))
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
        void addOneToManyRelationData() throws Exception {
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
        void addManyToManyRelationData() throws Exception {
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
        void removeOneToManyRelationData() throws Exception {
            mockMvc.perform(delete("/persons/{sourceId}/invoices/{targetId}", PERSON_ID, INVOICE_ID))
                    .andExpect(status().isNoContent());

            Mockito.verify(datamodelApi)
                    .removeRelationItem(TestApplication.APPLICATION, TestApplication.PERSON_INVOICES, PERSON_ID,
                            INVOICE_ID);
        }

        @Test
        void removeManyToManyRelationData() throws Exception {
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

        @ParameterizedTest
        @CsvSource({
                "/invoices/01234567-89ab-cdef-0123-456789abcdef/previous", // non-existent relation
                "/invoice/01234567-89ab-cdef-0123-456789abcdef/previous-invoice", // non-existent entity
        })
        void followRelationInvalidUrl(String url) throws Exception {
            mockMvc.perform(get(url))
                    .andExpect(status().isNotFound());
        }

        @Test
        void setRelationNoData() throws Exception {
            mockMvc.perform(put("/invoices/{sourceId}/previous-invoice", INVOICE_ID)
                            .contentType("text/uri-list")
                            .content("%n".formatted()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void setRelationTooManyData() throws Exception {
            var target1 = EntityId.of(UUID.randomUUID());
            var target2 = EntityId.of(UUID.randomUUID());

            mockMvc.perform(put("/invoices/{sourceId}/previous-invoice", INVOICE_ID)
                            .contentType("text/uri-list")
                            .content("http://localhost/invoices/%s%nhttp://localhost/invoices/%s%n".formatted(target1, target2)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void setRelationToManyRelation() throws Exception {
            var targetId = EntityId.of(UUID.randomUUID());

            mockMvc.perform(put("/persons/{sourceId}/invoices", PERSON_ID) // one-to-many
                            .contentType("text/uri-list")
                            .content("http://localhost/invoices/%s%n".formatted(targetId)))
                    .andExpect(status().isBadRequest());
        }

        @ParameterizedTest
        @MethodSource("invalidUrls")
        void setRelationInvalidUrl(String url) throws Exception {
            mockMvc.perform(put("/invoices/{sourceId}/previous-invoice", INVOICE_ID)
                            .contentType("text/uri-list")
                            .content(url))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void addRelationNoData() throws Exception {
            mockMvc.perform(post("/persons/{sourceId}/invoices", PERSON_ID)
                            .contentType("text/uri-list")
                            .content("%n".formatted()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void addRelationToOneRelation() throws Exception {
            var targetId = EntityId.of(UUID.randomUUID());

            mockMvc.perform(post("/invoices/{sourceId}/previous-invoice", INVOICE_ID) // one-to-one
                            .contentType("text/uri-list")
                            .content("http://localhost/invoices/%s%n".formatted(targetId)))
                    .andExpect(status().isBadRequest());
        }

        @ParameterizedTest
        @MethodSource("invalidUrls")
        void addRelationInvalidUrl(String url) throws Exception {
            mockMvc.perform(post("/persons/{sourceId}/invoices", PERSON_ID)
                            .contentType("text/uri-list")
                            .content(url))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void removeRelationToOneRelation() throws Exception {
            var targetId = EntityId.of(UUID.randomUUID());

            mockMvc.perform(delete("/invoices/{sourceId}/previous-invoice/{targetId}", INVOICE_ID, targetId)) // one-to-one
                    .andExpect(status().isBadRequest());
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
                    .andExpect(status().isNotFound());
        }

        @Test
        void followToOneRelationTargetIdNotFound() throws Exception {
            Mockito.doReturn(Optional.empty()).when(datamodelApi)
                    .findRelationTarget(TestApplication.APPLICATION, TestApplication.INVOICE_PREVIOUS, INVOICE_ID);

            mockMvc.perform(get("/invoices/{sourceId}/previous-invoice", INVOICE_ID))
                    .andExpect(status().isNotFound());
        }

        @Test
        void followToManyRelationSourceIdNotFound() throws Exception {
            Mockito.doReturn(Optional.empty()).when(datamodelApi)
                    .findById(TestApplication.APPLICATION, TestApplication.PERSON, PERSON_ID);

            mockMvc.perform(get("/persons/{sourceId}/invoices", PERSON_ID))
                    .andExpect(status().isNotFound());
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
                    .andExpect(status().isNotFound());
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
                    .andExpect(status().isBadRequest());
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
                    .andExpect(status().isNotFound());
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
                    .andExpect(status().isBadRequest());
        }

        @Test
        void clearRelationEntityIdNotFound() throws Exception {
            Mockito.doThrow(ENTITY_ID_NOT_FOUND).when(datamodelApi)
                    .deleteRelation(TestApplication.APPLICATION, TestApplication.INVOICE_PREVIOUS, INVOICE_ID);

            mockMvc.perform(delete("/invoices/{sourceId}/previous-invoice", INVOICE_ID))
                    .andExpect(status().isNotFound());
        }

        @Test
        void clearRelationForeignKeyRequired() throws Exception {
            Mockito.doThrow(FOREIGN_KEY_REQUIRED).when(datamodelApi)
                    .deleteRelation(TestApplication.APPLICATION, TestApplication.INVOICE_PREVIOUS, INVOICE_ID);

            mockMvc.perform(delete("/invoices/{sourceId}/previous-invoice", INVOICE_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void removeRelationDataEntityIdNotFound() throws Exception {
            Mockito.doThrow(ENTITY_ID_NOT_FOUND).when(datamodelApi)
                    .removeRelationItem(TestApplication.APPLICATION, TestApplication.PERSON_INVOICES, PERSON_ID, INVOICE_ID);

            mockMvc.perform(delete("/persons/{sourceId}/invoices/{targetId}", PERSON_ID, INVOICE_ID))
                    .andExpect(status().isNotFound());
        }

        @Test
        void removeRelationDataForeignKeyRequired() throws Exception {
            Mockito.doThrow(FOREIGN_KEY_REQUIRED).when(datamodelApi)
                    .removeRelationItem(TestApplication.APPLICATION, TestApplication.PERSON_INVOICES, PERSON_ID, INVOICE_ID);

            mockMvc.perform(delete("/persons/{sourceId}/invoices/{targetId}", PERSON_ID, INVOICE_ID))
                    .andExpect(status().isBadRequest());
        }

    }

}