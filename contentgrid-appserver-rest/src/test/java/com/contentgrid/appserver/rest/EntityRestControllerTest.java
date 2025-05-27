package com.contentgrid.appserver.rest;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.domain.DatamodelApi;
import com.contentgrid.appserver.domain.DatamodelApiImpl;
import com.contentgrid.appserver.registry.ApplicationResolver;
import com.contentgrid.appserver.registry.SingleApplicationResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class EntityRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;


    static final Application APPLICATION = Application.builder()
            .name(ApplicationName.of("testapp"))
            .entity(Entity.builder()
                    .name(EntityName.of("product"))
                    .table(TableName.of("product"))
                    .pathSegment(PathSegmentName.of("products"))
                    .linkName(LinkName.of("products"))
                    .attribute(SimpleAttribute.builder()
                            .name(AttributeName.of("name"))
                            .description("Product name")
                            .column(ColumnName.of("name"))
                            .type(Type.TEXT)
                            .build()
                    )
                    .attribute(SimpleAttribute.builder()
                            .name(AttributeName.of("price"))
                            .description("Product price")
                            .column(ColumnName.of("price"))
                            .type(Type.DOUBLE)
                            .build()
                    )
                    .attribute(SimpleAttribute.builder()
                            .name(AttributeName.of("release_date"))
                            .description("Product release date")
                            .column(ColumnName.of("release_date"))
                            .type(Type.DATETIME)
                            .build()
                    )
                    .attribute(SimpleAttribute.builder()
                            .name(AttributeName.of("in_stock"))
                            .description("Is product in stock")
                            .column(ColumnName.of("in_stock"))
                            .type(Type.BOOLEAN)
                            .build()
                    )
                    .build())
            .build();

    static final TestQueryEngine TEST_QUERY_ENGINE = new TestQueryEngine();

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public DatamodelApi dmapi(DummyQueryEngine queryEngine) {
            return new DatamodelApiImpl(queryEngine);
        }

        @Bean
        @Primary
        public ApplicationResolver singleApplicationResolver() {
            return new SingleApplicationResolver(APPLICATION);
        }

        @Bean
        @Primary
        public DummyQueryEngine testQueryEngine() {
            // So we can clear it between tests
            return TEST_QUERY_ENGINE;
        }
    }

    static class TestQueryEngine extends DummyQueryEngine {
        public void reset() {
            super.entityInstances.clear();
        }
    }

    @BeforeEach
    void setup() {
        TEST_QUERY_ENGINE.reset();
    }

    @Test
    void testSuccessfullyCreateEntityInstance() throws Exception {
        Map<String, Object> product = new HashMap<>();
        product.put("id", UUID.randomUUID());
        product.put("name", "Test Product");
        product.put("price", 29.99);
        product.put("release_date", "2023-01-15T10:00:00Z");
        product.put("in_stock", true);

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product))
                        .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Test Product")))
                .andExpect(jsonPath("$.price", is(29.99)))
                .andExpect(jsonPath("$.release_date", notNullValue()))
                .andExpect(jsonPath("$.in_stock", is(true)))
                .andExpect(jsonPath("$._links.self.href", notNullValue()));
    }

    @Test
    void testSuccessfullyRetrieveEntityInstance() throws Exception {
        // First create an entity
        Map<String, Object> product = new HashMap<>();
        product.put("name", "Retrievable Product");
        product.put("price", 99.99);
        product.put("release_date", "2023-02-20T14:30:00Z");
        product.put("in_stock", true);

        String responseContent = mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Extract ID from created entity
        String id = objectMapper.readTree(responseContent).get("id").asText();

        // Then retrieve it
        mockMvc.perform(get("/products/" + id).accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(id)))
                .andExpect(jsonPath("$.name", is("Retrievable Product")))
                .andExpect(jsonPath("$.price", is(99.99)))
                .andExpect(jsonPath("$.in_stock", is(true)))
                .andExpect(jsonPath("$._links.self.href", notNullValue()));
    }

    @Test
    void testFailToCreateEntityWithInvalidPayloadStructure() throws Exception {
        Map<String, Object> invalidProduct = new HashMap<>();
        invalidProduct.put("id", UUID.randomUUID());
        invalidProduct.put("name", "Invalid Product");
        invalidProduct.put("price", "not-a-number"); // This should be a number
        invalidProduct.put("release_date", "2023-03-10T09:15:00Z");
        invalidProduct.put("in_stock", true);

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidProduct)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", notNullValue()))
                .andExpect(jsonPath("$.title", notNullValue()))
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    void testFailToCreateEntityWithInvalidJson() throws Exception {
        String invalidJson = "{\"name\": \"Broken JSON, \"price\": 19.99}"; // Missing quotes

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", notNullValue()))
                .andExpect(jsonPath("$.title", notNullValue()))
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    void testGetNonExistentEntityInstance() throws Exception {
        String nonExistentId = UUID.randomUUID().toString();

        mockMvc.perform(get("/products/" + nonExistentId))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetNonExistentEntityType() throws Exception {
        String nonExistentId = UUID.randomUUID().toString();

        mockMvc.perform(get("/foobars/" + nonExistentId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/foobars"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCreateNonExistentEntityType() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "fake");
        payload.put("value", 123);

        mockMvc.perform(post("/foobars")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isNotFound());
    }

    @Test
    void testListEntityInstances() throws Exception {
        // Create multiple products first
        Map<String, Object> product1 = new HashMap<>();
        product1.put("name", "First Product");
        product1.put("price", 19.99);
        product1.put("release_date", "2023-05-10T08:00:00Z");
        product1.put("in_stock", true);

        Map<String, Object> product2 = new HashMap<>();
        product2.put("name", "Second Product");
        product2.put("price", 49.99);
        product2.put("release_date", "2023-06-15T10:30:00Z");
        product2.put("in_stock", false);

        // Add first product
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product1)))
                .andExpect(status().isCreated());

        // Add second product
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product2)))
                .andExpect(status().isCreated());

        // Now test the list endpoint
        mockMvc.perform(get("/products")
                .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.item", notNullValue()))
                .andExpect(jsonPath("$._embedded.item.length()", is(2)))
                .andExpect(jsonPath("$._embedded.item[0].name", notNullValue()))
                .andExpect(jsonPath("$._embedded.item[1].name", notNullValue()))
                .andExpect(jsonPath("$._embedded.item[?(@.name=='First Product')].price", is(List.of(19.99))))
                .andExpect(jsonPath("$._embedded.item[?(@.name=='Second Product')].price", is(List.of(49.99))))
                .andExpect(jsonPath("$._embedded.item[?(@.name=='First Product')]._links.self.href", notNullValue()))
                .andExpect(jsonPath("$._embedded.item[?(@.name=='Second Product')]._links.self.href", notNullValue()));
    }

    @Test
    void testUpdateEntityInstance() throws Exception {
        // Initial values
        Map<String, Object> product = new HashMap<>();
        product.put("name", "Initial Product");
        product.put("price", 777.00);
        product.put("release_date", "2001-02-03T04:05:06Z");
        product.put("in_stock", true);

        String responseContent = mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(responseContent).get("id").asText();

        // New values
        Map<String, Object> updated = new HashMap<>();
        updated.put("name", "Updated Product");
        updated.put("price", 999.00);
        // leave release_date absent → it should not reuse existing value, unlike with PATCH
        updated.put("in_stock", true);

        // Update with PUT
        mockMvc.perform(put("/products/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(id)))
                .andExpect(jsonPath("$.name", is("Updated Product")))
                .andExpect(jsonPath("$.price", is(999.00)))
                .andExpect(jsonPath("$.release_date").doesNotExist())
                .andExpect(jsonPath("$.in_stock", is(true)))
                .andExpect(jsonPath("$._links.self.href", notNullValue()));
    }

    @Test
    void testUpdateNonExistentEntityType() throws Exception {
        // Initial values
        Map<String, Object> product = new HashMap<>();
        product.put("name", "Initial Product");
        product.put("price", 777.00);
        product.put("release_date", "2001-02-03T04:05:06Z");
        product.put("in_stock", true);

        String responseContent = mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(responseContent).get("id").asText();

        // New values
        Map<String, Object> updated = new HashMap<>();
        updated.put("name", "Updated Product");
        updated.put("price", 999.00);
        updated.put("in_stock", true);

        // Update with PUT (correct id but wrong path)
        mockMvc.perform(put("/foobars/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isNotFound());
    }

    @Test
    void testUpdateWithWrongId() throws Exception {
        // Create valid product
        Map<String, Object> product = new HashMap<>();
        product.put("name", "Initial Product");
        product.put("price", 99.99);
        product.put("release_date", "2023-01-01T00:00:00Z");
        product.put("in_stock", true);

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isCreated());

        // Now try to PUT to a non-existent ID
        String nonExistentId = UUID.randomUUID().toString();
        Map<String, Object> updatedProduct = new HashMap<>();
        updatedProduct.put("name", "Updated Product");
        updatedProduct.put("price", 199.99);
        updatedProduct.put("in_stock", false);

        mockMvc.perform(put("/products/" + nonExistentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatedProduct)))
                .andExpect(status().isNotFound());
    }

}