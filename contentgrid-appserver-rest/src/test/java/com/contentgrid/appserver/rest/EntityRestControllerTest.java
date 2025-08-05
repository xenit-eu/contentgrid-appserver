package com.contentgrid.appserver.rest;

import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contentgrid.appserver.domain.DatamodelApi;
import com.contentgrid.appserver.domain.DatamodelApiImpl;
import com.contentgrid.appserver.domain.data.DataEntry.FileDataEntry;
import com.contentgrid.appserver.query.engine.api.QueryEngine;
import com.contentgrid.appserver.query.engine.api.TableCreator;
import com.contentgrid.appserver.registry.SingleApplicationResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.LinkedMultiValueMap;

@SpringBootTest
@AutoConfigureMockMvc
class EntityRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public DatamodelApi dmapi(QueryEngine queryEngine) {
            return new DatamodelApiImpl(queryEngine);
        }

        @Bean
        @Primary
        public SingleApplicationResolver singleApplicationResolver() {
            return new SingleApplicationResolver(APPLICATION);
        }
    }

    interface MediaTypeConfiguration {
        MockHttpServletRequestBuilder configure(MockHttpServletRequestBuilder builder, Map<String, Object> requestData) throws Exception;
    }

    private static Stream<Map.Entry<String, Object>> flattenMap(Map<String, Object> map) {
        return map.entrySet()
                .stream()
                .flatMap(entry -> {
                    if(entry.getValue() instanceof Map<?,?> m) {
                        return flattenMap((Map<String, Object>) m).map(e  -> Map.entry(entry.getKey()+"."+e.getKey(), e.getValue()));
                    }
                    if(entry.getValue() instanceof List<?> l) {
                        return l.stream().map(item -> Map.entry(entry.getKey(), item));
                    }
                    return Stream.of(entry);
                });
    }

    static Stream<MediaTypeConfiguration> supportedMediaTypes() {
        var objectMapper = new ObjectMapper();
        return Stream.of(
                new MediaTypeConfiguration() {
                    @Override
                    public MockHttpServletRequestBuilder configure(MockHttpServletRequestBuilder builder,
                            Map<String, Object> requestData) throws Exception {
                        return builder.contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(requestData));
                    }

                    @Override
                    public String toString() {
                        return "json";
                    }
                },
                new MediaTypeConfiguration() {
                    @Override
                    public MockHttpServletRequestBuilder configure(MockHttpServletRequestBuilder builder,
                            Map<String, Object> requestData) throws Exception {
                        var fieldMap = new LinkedMultiValueMap<String, String>();
                        flattenMap(requestData)
                                .forEachOrdered(entry -> {
                                    fieldMap.add(entry.getKey(), entry.getValue().toString());
                                });

                        return builder.contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .formFields(fieldMap);
                    }

                    @Override
                    public String toString() {
                        return "form-urlencoded";
                    }
                },
                new MediaTypeConfiguration() {
                    @Override
                    public MockHttpServletRequestBuilder configure(MockHttpServletRequestBuilder builder,
                            Map<String, Object> requestData) throws Exception {
                        var request = builder.buildRequest(new MockServletContext());
                        var multipartRequestBuilder = MockMvcRequestBuilders.multipart(
                                HttpMethod.valueOf(request.getMethod()), request.getRequestURI());

                        var fieldMap = new LinkedMultiValueMap<String, String>();

                        flattenMap(requestData)
                                .forEachOrdered(entry -> {
                                    if (entry.getValue() instanceof FileDataEntry fileDataEntry) {
                                        try {
                                            multipartRequestBuilder.file(new MockMultipartFile(
                                                    entry.getKey(),
                                                    fileDataEntry.getFilename(),
                                                    fileDataEntry.getContentType(),
                                                    fileDataEntry.getInputStream()
                                            ));
                                        } catch (IOException e) {
                                            throw new UncheckedIOException(e);
                                        }
                                    } else {
                                        fieldMap.add(entry.getKey(), entry.getValue().toString());
                                    }
                                });

                        multipartRequestBuilder.params(fieldMap);

                        return multipartRequestBuilder;
                    }

                    @Override
                    public String toString() {
                        return "form-multipart";
                    }
                }
        );

    }

    @Autowired
    TableCreator tableCreator;

    @BeforeEach
    void setup() {
        tableCreator.createTables(APPLICATION);
    }

    @AfterEach
    void teardown() {
        tableCreator.dropTables(APPLICATION);
    }

    @ParameterizedTest
    @MethodSource("supportedMediaTypes")
    void testSuccessfullyCreateEntityInstance(MediaTypeConfiguration mediaTypeConfiguration) throws Exception {
        Map<String, Object> product = new HashMap<>();
        product.put("name", "Test Product");
        product.put("price", 29.99);
        product.put("release_date", "2023-01-15T10:00:00Z");
        product.put("in_stock", true);

        mockMvc.perform(mediaTypeConfiguration.configure(post("/products"), product)
                        .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Test Product")))
                .andExpect(jsonPath("$.price", is(29.99)))
                .andExpect(jsonPath("$.release_date", notNullValue()))
                .andExpect(jsonPath("$.in_stock", is(true)))
                .andExpect(jsonPath("$._links.self.href", notNullValue()))
                .andExpect(jsonPath("$._links.curies").isArray());
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

        // Then retrieve it with application/hal+json
        mockMvc.perform(get("/products/" + id).accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaTypes.HAL_JSON))
                .andExpect(jsonPath("$.id", is(id)))
                .andExpect(jsonPath("$.name", is("Retrievable Product")))
                .andExpect(jsonPath("$.price", is(99.99)))
                .andExpect(jsonPath("$.in_stock", is(true)))
                .andExpect(jsonPath("$._links.self.href", notNullValue()))
                .andExpect(jsonPath("$._links.cg:content[0].name", is("picture")))
                .andExpect(jsonPath("$._links.cg:relation[0].name", is("invoices")))
                .andExpect(jsonPath("$._links.cg:content[1]").doesNotExist())
                .andExpect(jsonPath("$._links.cg:relation[1]").doesNotExist())
                .andExpect(jsonPath("$._links.curies").isArray())
                .andExpect(jsonPath("$._templates").doesNotExist());

        // Then retrieve it with application/prs.hal-forms+json
        mockMvc.perform(get("/products/" + id).accept(MediaTypes.HAL_FORMS_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaTypes.HAL_FORMS_JSON))
                .andExpect(jsonPath("$.id", is(id)))
                .andExpect(jsonPath("$.name", is("Retrievable Product")))
                .andExpect(jsonPath("$.price", is(99.99)))
                .andExpect(jsonPath("$.in_stock", is(true)))
                .andExpect(jsonPath("$._links.self.href", notNullValue()))
                .andExpect(jsonPath("$._links.cg:content[0].name", is("picture")))
                .andExpect(jsonPath("$._links.cg:relation[0].name", is("invoices")))
                .andExpect(jsonPath("$._links.cg:content[1]").doesNotExist())
                .andExpect(jsonPath("$._links.cg:relation[1]").doesNotExist())
                .andExpect(jsonPath("$._links.curies").isArray())
                .andExpect(content().json("""
                        {
                            _templates: {
                                default: {
                                    method: "PUT",
                                    contentType: "application/json",
                                    properties: [{
                                        name: "name",
                                        type: "text",
                                        required: true
                                    }, {
                                        name: "description",
                                        type: "text"
                                    }, {
                                        name: "price",
                                        type: "number",
                                        required: true
                                    }, {
                                        name: "release_date",
                                        type: "datetime"
                                    }, {
                                        name: "in_stock",
                                        type: "checkbox"
                                    }, {
                                        name: "picture.filename",
                                        type: "text"
                                    }, {
                                        name: "picture.mimetype",
                                        type: "text"
                                    }]
                                },
                                delete: {
                                    method: "DELETE"
                                },
                                add-invoices: {
                                    method: "POST",
                                    target: "http://localhost/products/${ENTITY_ID}/invoices",
                                    contentType: "text/uri-list",
                                    properties: [{
                                        name: "invoices",
                                        type: "url",
                                        options: {
                                            link: {
                                                href: "http://localhost/invoices?page=0" // TODO: remove page=0 (ACC-2200)
                                            },
                                            minItems: 0
                                        }
                                    }]
                                },
                                clear-invoices: {
                                    method: "DELETE",
                                    target: "http://localhost/products/${ENTITY_ID}/invoices"
                                }
                            }
                        }
                        """.replace("${ENTITY_ID}", id)));
    }

    @ParameterizedTest
    @MethodSource("supportedMediaTypes")
    void testFailToCreateEntityWithInvalidPayloadStructure(MediaTypeConfiguration mediaTypeConfiguration) throws Exception {
        Map<String, Object> invalidProduct = new HashMap<>();
        invalidProduct.put("id", UUID.randomUUID());
        invalidProduct.put("name", "Invalid Product");
        invalidProduct.put("price", "not-a-number"); // This should be a number
        invalidProduct.put("release_date", "2023-03-10T09:15:00Z");
        invalidProduct.put("in_stock", true);

        mockMvc.perform(mediaTypeConfiguration.configure(post("/products"), invalidProduct))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", notNullValue()))
                .andExpect(jsonPath("$.title", notNullValue()))
                .andExpect(jsonPath("$.status", is(400)));
    }

    @ParameterizedTest
    @MethodSource("supportedMediaTypes")
    void failToCreateEntityWithDoubleForLong(MediaTypeConfiguration mediaTypeConfiguration) throws Exception {
        mockMvc.perform(mediaTypeConfiguration.configure(post("/persons"), Map.of(
                        "name", "test_user",
                        "vat", "XYZ",
                        "age", 12.3
                ))
        ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("https://contentgrid.cloud/problems/invalid-request-body/type")))
                .andExpect(jsonPath("$.property-path", is(List.of("age"))));
    }

    @ParameterizedTest
    @MethodSource("supportedMediaTypes")
    void succeedToCreateEntityWithLongForDouble(MediaTypeConfiguration mediaTypeConfiguration) throws Exception {
        var url = mockMvc.perform(mediaTypeConfiguration.configure(post("/products"), Map.of(
                        "name", "test product",
                        "price", 5
                )))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");

        mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price", is(5)));
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

    @ParameterizedTest
    @MethodSource("supportedMediaTypes")
    void testCreateNonExistentEntityType(MediaTypeConfiguration mediaTypeConfiguration) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "fake");
        payload.put("value", 123);

        mockMvc.perform(mediaTypeConfiguration.configure(post("/foobars"), payload))
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

        // Test the list endpoint with application/hal+json
        mockMvc.perform(get("/products")
                .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaTypes.HAL_JSON))
                .andExpect(jsonPath("$._embedded.item", notNullValue()))
                .andExpect(jsonPath("$._embedded.item.length()", is(2)))
                .andExpect(jsonPath("$._embedded.item[0].name", notNullValue()))
                .andExpect(jsonPath("$._embedded.item[1].name", notNullValue()))
                .andExpect(jsonPath("$._embedded.item[0]._templates").doesNotExist())
                .andExpect(jsonPath("$._embedded.item[1]._templates").doesNotExist())
                .andExpect(jsonPath("$._embedded.item[?(@.name=='First Product')].price", is(List.of(19.99))))
                .andExpect(jsonPath("$._embedded.item[?(@.name=='Second Product')].price", is(List.of(49.99))))
                .andExpect(jsonPath("$._embedded.item[?(@.name=='First Product')]._links.self.href", notNullValue()))
                .andExpect(jsonPath("$._embedded.item[?(@.name=='Second Product')]._links.self.href", notNullValue()))
                .andExpect(jsonPath("$._links.curies").isArray());

        // Test the list endpoint with application/prs.hal-forms+json
        mockMvc.perform(get("/products")
                        .accept(MediaTypes.HAL_FORMS_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaTypes.HAL_FORMS_JSON))
                .andExpect(jsonPath("$._embedded.item", notNullValue()))
                .andExpect(jsonPath("$._embedded.item.length()", is(2)))
                .andExpect(jsonPath("$._embedded.item[0].name", notNullValue()))
                .andExpect(jsonPath("$._embedded.item[1].name", notNullValue()))
                .andExpect(jsonPath("$._embedded.item[0]._templates").exists())
                .andExpect(jsonPath("$._embedded.item[1]._templates").exists())
                .andExpect(jsonPath("$._embedded.item[?(@.name=='First Product')].price", is(List.of(19.99))))
                .andExpect(jsonPath("$._embedded.item[?(@.name=='Second Product')].price", is(List.of(49.99))))
                .andExpect(jsonPath("$._embedded.item[?(@.name=='First Product')]._links.self.href", notNullValue()))
                .andExpect(jsonPath("$._embedded.item[?(@.name=='Second Product')]._links.self.href", notNullValue()))
                .andExpect(jsonPath("$._links.curies").isArray());
    }

    @ParameterizedTest
    @MethodSource("supportedMediaTypes")
    void testUpdateEntityInstance(MediaTypeConfiguration mediaTypeConfiguration) throws Exception {
        // Initial values
        Map<String, Object> product = new HashMap<>();
        product.put("name", "Initial Product");
        product.put("price", 777.00);
        product.put("release_date", "2001-02-03T04:05:06Z");
        product.put("in_stock", true);

        String responseContent = mockMvc.perform(mediaTypeConfiguration.configure(post("/products"), product))
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

    @ParameterizedTest
    @MethodSource("supportedMediaTypes")
    void testUpdateNonExistentEntityType(MediaTypeConfiguration mediaTypeConfiguration) throws Exception {
        // Initial values
        Map<String, Object> product = new HashMap<>();
        product.put("name", "Initial Product");
        product.put("price", 777.00);
        product.put("release_date", "2001-02-03T04:05:06Z");
        product.put("in_stock", true);

        String responseContent = mockMvc.perform(mediaTypeConfiguration.configure(post("/products"), product))
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

    @ParameterizedTest
    @MethodSource("supportedMediaTypes")
    void testUpdateWithWrongId(MediaTypeConfiguration mediaTypeConfiguration) throws Exception {
        // Create valid product
        Map<String, Object> product = new HashMap<>();
        product.put("name", "Initial Product");
        product.put("price", 99.99);
        product.put("release_date", "2023-01-01T00:00:00Z");
        product.put("in_stock", true);

        mockMvc.perform(mediaTypeConfiguration.configure(post("/products"), product))
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

    @Test
    void testSorting() throws Exception {
        // Create entity with less-sorting price but greater-sorting name
        Map<String, Object> product = new HashMap<>();
        product.put("name", "Nines");
        product.put("price", 99.99);
        product.put("release_date", "2023-02-20T14:30:00Z");

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Create entity with greater-sorting price but less-sorting name
        Map<String, Object> product2 = new HashMap<>();
        product.put("name", "Hundred");
        product.put("price", 100.0);
        product.put("release_date", "2022-02-22T22:22:22Z");

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // List by ascending price
        mockMvc.perform(get("/products?_sort=price,asc").accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.item[0].name", is("Nines")))
                .andExpect(jsonPath("$._embedded.item[1].name", is("Hundred")));

        // Then list by descending price
        mockMvc.perform(get("/products?_sort=price,desc").accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.item[0].name", is("Hundred")))
                .andExpect(jsonPath("$._embedded.item[1].name", is("Nines")));

        // List by ascending name
        mockMvc.perform(get("/products?_sort=name,asc").accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.item[0].name", is("Hundred")))
                .andExpect(jsonPath("$._embedded.item[1].name", is("Nines")));

        // Then list by descending name
        mockMvc.perform(get("/products?_sort=name,desc").accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.item[0].name", is("Nines")))
                .andExpect(jsonPath("$._embedded.item[1].name", is("Hundred")));
    }

    @Test
    void testInvalidSort() throws Exception {
        Map<String, Object> product = new HashMap<>();
        product.put("name", "Nines");
        product.put("price", 99.99);
        product.put("release_date", "2023-02-20T14:30:00Z");

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Invalid sort direction
        mockMvc.perform(get("/products?_sort=price,foo").accept(MediaTypes.HAL_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://contentgrid.cloud/problems/invalid-query-parameter/sort"))
                .andExpect(jsonPath("$.detail").value(containsString("Invalid sort direction")));

        // Invalid sort field
        mockMvc.perform(get("/products?_sort=foo,desc").accept(MediaTypes.HAL_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://contentgrid.cloud/problems/invalid-query-parameter/sort"))
                .andExpect(jsonPath("$.detail").value(containsString("not found")));
    }

}