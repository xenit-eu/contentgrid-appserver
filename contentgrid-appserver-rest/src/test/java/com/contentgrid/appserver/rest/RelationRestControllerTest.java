package com.contentgrid.appserver.rest;

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
import com.contentgrid.appserver.registry.ApplicationResolver;
import com.contentgrid.appserver.registry.SingleApplicationResolver;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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
class RelationRestControllerTest {

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

    @Test
    void followOneToOneRelation() throws Exception {
        var targetId = EntityId.of(UUID.randomUUID());

        Mockito.doReturn(Optional.of(targetId)).when(datamodelApi)
                .findRelationTarget(TestApplication.APPLICATION, TestApplication.INVOICE_PREVIOUS, INVOICE_ID);

        mockMvc.perform(get("/invoices/{sourceId}/previous-invoice", INVOICE_ID))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "http://localhost/invoices/%s".formatted(targetId)));
    }

    @Test
    void followManyToOneRelation() throws Exception {
        var targetId = EntityId.of(UUID.randomUUID());

        Mockito.doReturn(Optional.of(targetId)).when(datamodelApi)
                .findRelationTarget(TestApplication.APPLICATION, TestApplication.INVOICE_CUSTOMER, INVOICE_ID);

        mockMvc.perform(get("/invoices/{sourceId}/customer", INVOICE_ID))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "http://localhost/persons/%s".formatted(targetId)));
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
                .andExpect(header().string(HttpHeaders.LOCATION, "http://localhost/invoices?page=0&customer=%s".formatted(PERSON_ID)));
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
                .andExpect(header().string(HttpHeaders.LOCATION, "http://localhost/invoices?page=0&products=%s".formatted(PRODUCT_ID)));
    }

    @Test
    void setOneToOneRelation() throws Exception {
        var targetId = EntityId.of(UUID.randomUUID());

        mockMvc.perform(put("/invoices/{sourceId}/previous-invoice", INVOICE_ID)
                        .contentType("text/uri-list")
                        .content("http://localhost/invoices/%s\n".formatted(targetId)))
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
                        .content("http://localhost/persons/%s\n".formatted(targetId)))
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
                        .content("http://localhost/invoices/%s\nhttp://localhost/invoices/%s\n".formatted(invoice1, invoice2)))
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
                        .content("http://localhost/invoices/%s\nhttp://localhost/invoices/%s\n".formatted(invoice1, invoice2)))
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
                .removeRelationItem(TestApplication.APPLICATION, TestApplication.PERSON_INVOICES, PERSON_ID, INVOICE_ID);
    }

    @Test
    void removeManyToManyRelationData() throws Exception {
        mockMvc.perform(delete("/products/{sourceId}/invoices/{targetId}", PRODUCT_ID, INVOICE_ID))
                .andExpect(status().isNoContent());

        Mockito.verify(datamodelApi)
                .removeRelationItem(TestApplication.APPLICATION, TestApplication.PRODUCT_INVOICES, PRODUCT_ID, INVOICE_ID);
    }

}