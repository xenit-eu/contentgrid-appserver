package com.contentgrid.appserver.rest;

import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.domain.data.DataEntry.FileDataEntry;
import com.contentgrid.appserver.example.ContentgridApp;
import com.contentgrid.appserver.query.engine.api.TableCreator;
import com.contentgrid.appserver.registry.SingleApplicationResolver;
import com.contentgrid.appserver.rest.EntityRestControllerTest.TestConfig;
import com.contentgrid.appserver.rest.test.ProblemDetailsMockMvcMatchers;
import com.contentgrid.appserver.rest.test.WithMockJwt;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.LinkedMultiValueMap;

@SpringBootTest(classes = {ContentgridApp.class, TestConfig.class}, properties = {
        "contentgrid.thunx.abac.source=none",
        "contentgrid.appserver.content-store.type=ephemeral",
        "contentgrid.events.rabbitmq.enabled=false",
})
@AutoConfigureMockMvc
@WithMockJwt
class EntityRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        public SingleApplicationResolver singleApplicationResolver() {
            return new SingleApplicationResolver(APPLICATION);
        }

        @Bean
        Clock fixedClock() {
            return Clock.fixed(Instant.ofEpochSecond(1234567890), ZoneOffset.UTC);
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

    private MockHttpServletResponse createPerson() throws Exception {
        return mockMvc.perform(post("/persons")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "test")
                        .param("vat", UUID.randomUUID().toString())
                )
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(HttpHeaders.ETAG))
                .andReturn()
                .getResponse();
    }

    private MockHttpServletResponse createInvoice() throws Exception {
        var personCreate = createPerson()
                .getRedirectedUrl();

        return mockMvc.perform(post("/invoices")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("number", "123")
                        .param("amount", "150")
                        .param("confidentiality", "secret")
                        .param("customer", personCreate)
                )
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andReturn()
                .getResponse();
    }

    private MockHttpServletResponse createProduct(int i) throws Exception {
        return mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "product_" + i)
                        .param("price", String.valueOf(i * 20.0))
                        .param("is_paid", String.valueOf(i % 2 == 1)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse();
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

    @Nested
    class CreateEntity {

        @ParameterizedTest
        @MethodSource("com.contentgrid.appserver.rest.EntityRestControllerTest#supportedMediaTypes")
        void testSuccessfullyCreateEntityInstance(MediaTypeConfiguration mediaTypeConfiguration) throws Exception {
            Map<String, Object> product = new HashMap<>();
            product.put("name", "Test Product");
            product.put("price", 29.99);
            product.put("release_date", "2023-01-15T10:00:00Z");
            product.put("in_stock", true);

            mockMvc.perform(mediaTypeConfiguration.configure(post("/products"), product)
                            .accept(MediaTypes.HAL_JSON))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists(HttpHeaders.ETAG))
                    .andExpect(jsonPath("$.id", notNullValue()))
                    .andExpect(jsonPath("$.name", is("Test Product")))
                    .andExpect(jsonPath("$.price", is(29.99)))
                    .andExpect(jsonPath("$.release_date", notNullValue()))
                    .andExpect(jsonPath("$.in_stock", is(true)))
                    .andExpect(jsonPath("$._links.self.href", notNullValue()))
                    .andExpect(jsonPath("$._links.curies").isArray());
        }

        @Test
        void testCreateEntityInstanceMultipartFileUpload() throws Exception {
            mockMvc.perform(multipart("/products")
                            .file(new MockMultipartFile("picture", "IMG_456.jpg", "application/jpeg",
                                    InputStream.nullInputStream()))
                            .param("name", "My product")
                            .param("price", "120")
                    ).andExpect(status().isCreated())
                    .andExpect(header().exists(HttpHeaders.ETAG))
                    .andExpect(jsonPath("$.picture.filename", is("IMG_456.jpg")))
                    .andExpect(jsonPath("$.picture.mimetype", is("application/jpeg")))
                    .andExpect(jsonPath("$.picture.length", is(0)))
            ;
        }

        @Test
        void testFailToCreateEntityInstanceMultipartMissingContentType() throws Exception {
            mockMvc.perform(multipart("/products")
                            .file(new MockMultipartFile("picture", "IMG_456.jpg", null, InputStream.nullInputStream()))
                            .param("name", "My product")
                            .param("price", "120")
                    ).andExpect(status().isBadRequest())
                    .andExpect(ProblemDetailsMockMvcMatchers.validationConstraintViolation()
                            .withError(e -> e.withType("https://contentgrid.cloud/problems/input/validation/type/format")
                                    .withTitle("Invalid format")
                                    .withDetail("Expected value of type content, but the format is incorrect: Content-Type is required")
                                    .withField("expected-type", "content")
                                    .withField("format-error", "Content-Type is required")
                                    .withProperty("picture")
                            )
                    )
            ;
        }

        @ParameterizedTest
        @MethodSource("com.contentgrid.appserver.rest.EntityRestControllerTest#supportedMediaTypes")
        void testFailToCreateEntityWithInvalidPayloadStructure(MediaTypeConfiguration mediaTypeConfiguration)
                throws Exception {
            Map<String, Object> invalidProduct = new HashMap<>();
            invalidProduct.put("id", UUID.randomUUID());
            invalidProduct.put("name", "Invalid Product");
            invalidProduct.put("price", "not-a-number"); // This should be a number
            invalidProduct.put("release_date", "2023-03-10T09:15:00Z");
            invalidProduct.put("in_stock", true);

            mockMvc.perform(mediaTypeConfiguration.configure(post("/products"), invalidProduct))
                    .andExpect(ProblemDetailsMockMvcMatchers.validationConstraintViolation()
                            .withError(e -> e.withType("https://contentgrid.cloud/problems/input/validation/type/format")
                                    .withTitle("Invalid format")
                                    .withDetail(d -> assertThat(d).startsWith("Expected value of type decimal, but the format is incorrect:"))
                                    .withProperty("price")
                                    .withField("expected-type", "decimal")
                                    .withField("format-error", f -> assertThat(f).isNotNull())
                            ));
        }

        @ParameterizedTest
        @MethodSource("com.contentgrid.appserver.rest.EntityRestControllerTest#supportedMediaTypes")
        void failToCreateEntityWithDoubleForLong(MediaTypeConfiguration mediaTypeConfiguration) throws Exception {
            mockMvc.perform(mediaTypeConfiguration.configure(post("/persons"), Map.of(
                                    "name", "test_user",
                                    "vat", "XYZ",
                                    "age", 12.3
                            ))
                    ).andExpect(status().isBadRequest())
                    .andExpect(ProblemDetailsMockMvcMatchers.validationConstraintViolation()
                            .withError(e -> e.withType("https://contentgrid.cloud/problems/input/validation/type/format")
                                    .withTitle("Invalid format")
                                    .withDetail(d -> assertThat(d).startsWith("Expected value of type long, but the format is incorrect:"))
                                    .withField("expected-type", "long")
                                    .withField("format-error", f -> assertThat(f).isNotNull())
                                    .withProperty("age")
                            )
                    );
        }

        @ParameterizedTest
        @MethodSource("com.contentgrid.appserver.rest.EntityRestControllerTest#supportedMediaTypes")
        void succeedToCreateEntityWithLongForDouble(MediaTypeConfiguration mediaTypeConfiguration) throws Exception {
            var url = mockMvc.perform(mediaTypeConfiguration.configure(post("/products"), Map.of(
                            "name", "test product",
                            "price", 5
                    )))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists(HttpHeaders.ETAG))
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
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                            .withStatusCode(HttpStatus.BAD_REQUEST)
                            .withType("https://contentgrid.cloud/problems/invalid-request-body/json")
                            .withTitle("Request body is invalid JSON")
                    );
        }

        @ParameterizedTest
        @MethodSource("com.contentgrid.appserver.rest.EntityRestControllerTest#supportedMediaTypes")
        void testCreateNonExistentEntityType(MediaTypeConfiguration mediaTypeConfiguration) throws Exception {
            Map<String, Object> payload = new HashMap<>();
            payload.put("name", "fake");
            payload.put("value", 123);

            mockMvc.perform(mediaTypeConfiguration.configure(post("/foobars"), payload))
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                            .withStatusCode(HttpStatus.NOT_FOUND)
                            .withType("https://contentgrid.cloud/problems/not-found/entity-definition")
                            .withTitle("Entity or resource not found")
                            .withDetail("No entity or static resource foobars")
                    );
        }

        @ParameterizedTest
        @MethodSource("com.contentgrid.appserver.rest.EntityRestControllerTest#supportedMediaTypes")
        void testFailToCreateWithContentFileNameAndMimetype(MediaTypeConfiguration mediaTypeConfiguration) throws Exception {
            Map<String, Object> product = new HashMap<>();
            product.put("name", "Test Product");
            product.put("price", 29.99);
            product.put("release_date", "2023-01-15T10:00:00Z");
            product.put("in_stock", true);
            product.put("picture", Map.of("filename", "picture.jpg", "mimetype", "application/jpeg"));

            mockMvc.perform(mediaTypeConfiguration.configure(post("/products"), product)
                            .accept(MediaTypes.HAL_JSON))
                    .andExpect(ProblemDetailsMockMvcMatchers.validationConstraintViolation()
                            .withError(e -> e.withType("https://contentgrid.cloud/problems/input/validation/no-content")
                                    .withTitle("No content present")
                                    .withDetail("Content attributes can not be set when there is no content present")
                                    .withProperty("picture")
                            )
                    );
        }
    }

    @Nested
    class GetEntity {

        @Test
        void getEntityWithoutContent() throws Exception {
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
                    .andExpect(header().exists(HttpHeaders.ETAG))
                    .andReturn().getResponse().getContentAsString();

            // Extract ID from created entity
            String id = objectMapper.readTree(responseContent).get("id").asText();

            // Then retrieve it with application/hal+json
            mockMvc.perform(get("/products/" + id).accept(MediaTypes.HAL_JSON))
                    .andExpect(status().isOk())
                    .andExpect(header().exists(HttpHeaders.ETAG))
                    .andExpect(content().contentType(MediaTypes.HAL_JSON))
                    .andExpect(jsonPath("$.id", is(id)))
                    .andExpect(jsonPath("$.name", is("Retrievable Product")))
                    .andExpect(jsonPath("$.price", is(99.99)))
                    .andExpect(jsonPath("$.in_stock", is(true)))
                    .andExpect(jsonPath("$.release_date", is("2023-02-20T14:30:00Z")))
                    .andExpect(jsonPath("$.picture", nullValue()))
                    .andExpect(jsonPath("$._links.self.href", notNullValue()))
                    .andExpect(jsonPath("$._links.cg:content[0].name", is("picture")))
                    .andExpect(jsonPath("$._links.cg:relation[0].name", is("invoices")))
                    .andExpect(jsonPath("$._links.cg:content[1]").doesNotExist())
                    .andExpect(jsonPath("$._links.cg:relation[1]").doesNotExist())
                    .andExpect(jsonPath("$._links.curies").isArray())
                    .andExpect(jsonPath("$._templates").doesNotExist())
                    .andExpect(jsonPath("$._version").doesNotHaveJsonPath());

            // Then retrieve it with application/prs.hal-forms+json
            mockMvc.perform(get("/products/" + id).accept(MediaTypes.HAL_FORMS_JSON))
                    .andExpect(status().isOk())
                    .andExpect(header().exists(HttpHeaders.ETAG))
                    .andExpect(content().contentType(MediaTypes.HAL_FORMS_JSON))
                    .andExpect(jsonPath("$.id", is(id)))
                    .andExpect(jsonPath("$.name", is("Retrievable Product")))
                    .andExpect(jsonPath("$.price", is(99.99)))
                    .andExpect(jsonPath("$.in_stock", is(true)))
                    .andExpect(jsonPath("$.release_date", is("2023-02-20T14:30:00Z")))
                    .andExpect(jsonPath("$.picture", nullValue()))
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
                                                    href: "http://localhost/invoices"
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

        @Test
        void getEntityWithContent() throws Exception {
            var invoice = createInvoice();

            mockMvc.perform(post(invoice.getRedirectedUrl()+"/content")
                    .header(HttpHeaders.IF_NONE_MATCH, "*")
                    .contentType("text/plain")
                    .content("My small content")
            ).andExpect(status().isNoContent());

            mockMvc.perform(get(invoice.getRedirectedUrl()))
                    .andExpect(status().isOk())
                    .andExpect(header().exists(HttpHeaders.ETAG))
                    .andExpect(jsonPath("$.pay_before", nullValue()))
                    .andExpect(jsonPath("$.content.id").doesNotExist())
                    .andExpect(jsonPath("$.content.filename", nullValue()))
                    .andExpect(jsonPath("$.content.mimetype", is("text/plain;charset=UTF-8")))
                    .andExpect(jsonPath("$.content.length", is(16)));
        }

        @Test
        void getEntity_translations() throws Exception {
            var person = createPerson();

            mockMvc.perform(get(person.getRedirectedUrl())
                    .accept(MediaTypes.HAL_FORMS_JSON)
                    .header(HttpHeaders.ACCEPT_LANGUAGE, "nl, fr, en")
            ).andExpect(status().isOk())
                    .andExpect(content().json("""
                            {
                                _links: {
                                    self: {
                                        title: "Persoon"
                                    },
                                    "cg:relation": [
                                        {
                                            name: "invoices",
                                            title: "invoices"
                                        },
                                        {
                                            name: "friends",
                                            title: "friends"
                                        },
                                        {
                                            name: "children",
                                            title: "children"
                                        },
                                        {
                                            name: "parent",
                                            title: "Parent(e)"
                                        }
                                    ]
                                },
                                _templates: {
                                    "set-parent": {
                                        properties: [
                                            {
                                                name: "parent",
                                                prompt: "Parent(e)",
                                                options: {
                                                    link: {
                                                        href: "http://localhost/persons",
                                                        title: "Personen"
                                                    }
                                                }
                                            }
                                        ]
                                    },
                                    default: {
                                        method: "PUT",
                                        properties: [
                                            {
                                                name: "name",
                                                prompt: "Naam"
                                            },
                                            {
                                                name: "vat",
                                                prompt: "BTW nummer"
                                            },
                                            {
                                                name: "age",
                                                prompt: "Âge"
                                            },
                                            {
                                                name: "gender",
                                                prompt: "gender"
                                            }
                                        ]
                                    }
                                }
                            }
                            """));
        }

        @Test
        void testGetNonExistentEntityInstance() throws Exception {
            String nonExistentId = UUID.randomUUID().toString();

            mockMvc.perform(get("/products/" + nonExistentId))
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                            .withStatusCode(HttpStatus.NOT_FOUND)
                            .withType("https://contentgrid.cloud/problems/not-found/entity-item")
                            .withTitle("Entity item not found")
                            .withDetail("No entity 'product' found with id '" + nonExistentId + "'")
                    );
        }

        @Test
        void testGetNonExistentEntityType() throws Exception {
            String nonExistentId = UUID.randomUUID().toString();

            mockMvc.perform(get("/foobars/" + nonExistentId))
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                                    .withStatusCode(HttpStatus.NOT_FOUND)
                                    .withType("https://contentgrid.cloud/problems/not-found/entity-definition")
                                    .withTitle("Entity or resource not found")
                    );
            mockMvc.perform(get("/foobars"))
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                                    .withStatusCode(HttpStatus.NOT_FOUND)
                                    .withType("https://contentgrid.cloud/problems/not-found/entity-definition")
                                    .withTitle("Entity or resource not found")
                    );
        }
    }

    @Nested
    class ListEntity {

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
                    .andExpect(jsonPath("$._links.self.href").exists())
                    .andExpect(jsonPath("$._links.profile.href").exists())
                    .andExpect(jsonPath("$._embedded.item", notNullValue()))
                    .andExpect(jsonPath("$._embedded.item.length()", is(2)))
                    .andExpect(jsonPath("$._embedded.item[0].name", notNullValue()))
                    .andExpect(jsonPath("$._embedded.item[1].name", notNullValue()))
                    .andExpect(jsonPath("$._embedded.item[0]._templates").doesNotExist())
                    .andExpect(jsonPath("$._embedded.item[1]._templates").doesNotExist())
                    .andExpect(jsonPath("$._embedded.item[?(@.name=='First Product')].price", is(List.of(19.99))))
                    .andExpect(jsonPath("$._embedded.item[?(@.name=='Second Product')].price", is(List.of(49.99))))
                    .andExpect(
                            jsonPath("$._embedded.item[?(@.name=='First Product')]._links.self.href", notNullValue()))
                    .andExpect(
                            jsonPath("$._embedded.item[?(@.name=='Second Product')]._links.self.href", notNullValue()))
                    .andExpect(jsonPath("$._links.curies").isArray())
                    .andExpect(jsonPath("$.page").exists());

            // Test the list endpoint with application/prs.hal-forms+json
            mockMvc.perform(get("/products")
                            .accept(MediaTypes.HAL_FORMS_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaTypes.HAL_FORMS_JSON))
                    .andExpect(jsonPath("$._links.self.href").exists())
                    .andExpect(jsonPath("$._links.profile.href").exists())
                    .andExpect(jsonPath("$._embedded.item", notNullValue()))
                    .andExpect(jsonPath("$._embedded.item.length()", is(2)))
                    .andExpect(jsonPath("$._embedded.item[0].name", notNullValue()))
                    .andExpect(jsonPath("$._embedded.item[1].name", notNullValue()))
                    .andExpect(jsonPath("$._embedded.item[0]._templates").exists())
                    .andExpect(jsonPath("$._embedded.item[1]._templates").exists())
                    .andExpect(jsonPath("$._embedded.item[?(@.name=='First Product')].price", is(List.of(19.99))))
                    .andExpect(jsonPath("$._embedded.item[?(@.name=='Second Product')].price", is(List.of(49.99))))
                    .andExpect(
                            jsonPath("$._embedded.item[?(@.name=='First Product')]._links.self.href", notNullValue()))
                    .andExpect(
                            jsonPath("$._embedded.item[?(@.name=='Second Product')]._links.self.href", notNullValue()))
                    .andExpect(jsonPath("$._links.curies").isArray())
                    .andExpect(jsonPath("$.page").exists());
        }

        @Test
        void testListEntityInstances_emptyResults() throws Exception {
            mockMvc.perform(get("/products").accept(MediaTypes.HAL_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaTypes.HAL_JSON))
                    .andExpect(jsonPath("$._embedded.item").isArray())
                    .andExpect(jsonPath("$._embedded.item[0]").doesNotExist());
        }

        static Stream<Arguments> testListEntityInstances_withQueryParam() {
            return Stream.of(
                    Arguments.of("?number=invoice1", 1),
                    Arguments.of("?amount=12.0", 1),
                    Arguments.of("?amount~gt=10.0&amount~lt=20.0", 1),
                    Arguments.of("?amount~gte=12.0&amount~lte=12.5", 1),
                    Arguments.of("?received~after=2024-01-01&received~before=2025-01-02", 1),
                    Arguments.of("?received~from=2024-01-01&received~to=2025-01-01", 1),
                    Arguments.of("?pay_before~after=2025-01-31&pay_before~before=2026-01-31", 1),
                    Arguments.of("?pay_before~from=2025-01-31&pay_before~to=2025-02-28", 1),
                    Arguments.of("?pay_timestamp~after=2025-01-02T00:00:00.000Z&pay_timestamp~before=2025-01-02T23:59:59.999Z", 1),
                    Arguments.of("?confidentiality=public", 1),
                    Arguments.of("?customer=00000000-0000-0000-0000-000000000000", 0),
                    Arguments.of("?customer.name~prefix=a", 1),
                    Arguments.of("?customer.vat=vat1", 1),
                    Arguments.of("?previous_invoice.number=invoice1", 1),
                    Arguments.of("?previous_invoice.confidentiality=confidential", 1),
                    Arguments.of("?next_invoice.number=invoice2", 1),
                    Arguments.of("?next_invoice.confidentiality=public", 1)
            );
        }

        @ParameterizedTest
        @MethodSource
        void testListEntityInstances_withQueryParam(String queryParams, int results) throws Exception {
            var person1 = new HashMap<String, Object>();
            person1.put("name", "Alice");
            person1.put("vat", "vat1");
            person1.put("age", 12);

            // Add first person
            var person1response = mockMvc.perform(post("/persons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(person1)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse();

            var person1link = person1response.getHeader(HttpHeaders.LOCATION);
            assertThat(person1link).isNotBlank();

            var invoice1 = new HashMap<String, Object>();
            invoice1.put("number", "invoice1");
            invoice1.put("amount", 12.0);
            invoice1.put("received", "2025-01-01");
            invoice1.put("pay_before", "2026-01-01");
            invoice1.put("pay_timestamp", "2025-06-05T04:03:02.001Z");
            invoice1.put("is_paid", true);
            invoice1.put("confidentiality", "confidential");
            invoice1.put("customer", person1link);

            // Add first invoice
            var invoice1response = mockMvc.perform(post("/invoices")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invoice1)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse();

            var invoice1link = invoice1response.getHeader(HttpHeaders.LOCATION);
            assertThat(invoice1link).isNotBlank();

            var person2 = new HashMap<String, Object>();
            person2.put("name", "Bob");
            person2.put("vat", "vat2");
            person2.put("age", 20);

            // Add second person
            var person2response = mockMvc.perform(post("/persons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(person2)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse();

            var person2link = person2response.getHeader(HttpHeaders.LOCATION);
            assertThat(person2link).isNotBlank();

            var invoice2 = new HashMap<String, Object>();
            invoice2.put("number", "invoice2");
            invoice2.put("amount", 20.0);
            invoice2.put("received", "2025-01-02");
            invoice2.put("pay_before", "2025-01-31");
            invoice2.put("pay_timestamp", "2025-01-02T03:04:05.006Z");
            invoice2.put("is_paid", false);
            invoice2.put("confidentiality", "public");
            invoice2.put("customer", person2link);
            invoice2.put("previous_invoice", invoice1link);

            // Add second invoice
            mockMvc.perform(post("/invoices")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invoice2)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse();

            mockMvc.perform(get("/invoices" + queryParams))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$._embedded.item.length()", is(results)));
        }

        static Stream<Arguments> testListEntityInstances_withQueryParam_invalidValue() {
            return Stream.of(
                    Arguments.of("amount", "not+a+decimal", Type.DOUBLE),
                    Arguments.of("amount~gt", "not+a+decimal", Type.DOUBLE),
                    Arguments.of("amount~gte", "not+a+decimal", Type.DOUBLE),
                    Arguments.of("received~after", "2024-01-01T00:00:00.000Z", Type.DATE),
                    Arguments.of("received~from", "not+a+date", Type.DATE),
                    Arguments.of("pay_before~after", "2025-01-01T01:01:01.001Z", Type.DATE),
                    Arguments.of("pay_before~from", "not+a+date", Type.DATE),
                    Arguments.of("pay_timestamp~after", "2025-01-02", Type.DATETIME),
                    Arguments.of("customer", "not+a+uuid", Type.UUID)
            );
        }

        @ParameterizedTest
        @MethodSource
        void testListEntityInstances_withQueryParam_invalidValue(String queryParam, String value, Type type) throws Exception {
            mockMvc.perform(get("/invoices?" + queryParam + "=" + value))
                    .andExpect(status().isBadRequest())
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                            .withStatusCode(HttpStatus.BAD_REQUEST)
                            .withType("https://contentgrid.cloud/problems/invalid-filter-parameter/format")
                            .withTitle("Filter query parameter has an invalid format")
                            .withDetail("Invalid argument for filter %s in entity invoice: Could not convert value '%s' to %s"
                                    .formatted(queryParam, value, type))
                    );
        }

        @Test
        void testListEntityInstances_withSorting() throws Exception {
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
            product2.put("name", "Hundred");
            product2.put("price", 100.0);
            product2.put("release_date", "2022-02-22T22:22:22Z");

            mockMvc.perform(post("/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(product2)))
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
        void testListEntityInstances_withInvalidSort() throws Exception {
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

            // Multiple invalid sort directions
            mockMvc.perform(get("/products?_sort=price,foo&_sort=name,desc&_sort=name,bar").accept(MediaTypes.HAL_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value("https://contentgrid.cloud/problems/invalid-query-parameter/sort"))
                    .andExpect(jsonPath("$.detail").value(containsString("Invalid sort direction")))
                    .andExpect(jsonPath("$.all-errors").isArray())
                    .andExpect(jsonPath("$.all-errors[0].detail").value(containsString("foo")))
                    .andExpect(jsonPath("$.all-errors[1].detail").value(containsString("bar")));

            // Invalid sort field
            mockMvc.perform(get("/products?_sort=foo,desc").accept(MediaTypes.HAL_JSON))
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                            .withStatusCode(HttpStatus.BAD_REQUEST)
                            .withType("https://contentgrid.cloud/problems/invalid-query-parameter/sort")
                            .withTitle("Sort query parameter is invalid")
                            .withDetail("Sortable field 'foo' not found on entity 'product'")
                    );
        }

        @Test
        void testListEntityInstances_withPaging() throws Exception {
            for (var i = 0; i < 100; i++) {
                createProduct(i);
            }
            // Request default page
            mockMvc.perform(get("/products")
                            .accept(MediaTypes.HAL_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$._embedded.item.length()", is(20)))
                    .andExpect(jsonPath("$._embedded.item[?(@.name=='product_1')].price", is(List.of(20.00))))
                    .andExpect(jsonPath("$._embedded.item[?(@.name=='product_2')].price", is(List.of(40.00))))

                    // Check pagination links
                    .andExpect(jsonPath("$._links.self.href", containsString("_cursor")))
                    .andExpect(jsonPath("$._links.first.href").exists())
                    .andExpect(jsonPath("$._links.prev.href").doesNotExist())
                    .andExpect(jsonPath("$._links.next.href").exists())
                    .andExpect(jsonPath("$._links.last.href").doesNotExist())

                    // Check pagination metadata
                    .andExpect(jsonPath("$.page.size", is(20)))
                    .andExpect(jsonPath("$.page.prev_cursor").doesNotExist())
                    .andExpect(jsonPath("$.page.next_cursor").exists())
                    .andExpect(jsonPath("$.page.total_items_estimate", is(100)))
                    .andExpect(jsonPath("$.page.total_items_exact", is(100)))
                    // Check legacy properties don't exist
                    .andExpect(jsonPath("$.page.number").doesNotExist())
                    .andExpect(jsonPath("$.page.totalElements").doesNotExist())
                    .andExpect(jsonPath("$.page.totalPages").doesNotExist())
                    .andReturn()
                    .getResponse();
        }

        @Test
        void testListEntityInstances_withPaging_allPagingParameters() throws Exception {
            for (var i = 0; i < 100; i++) {
                createProduct(i);
            }
            // Request first page
            var firstPage = mockMvc.perform(get("/products?_size=10&_sort=price,asc&_sort=name,desc")
                            .accept(MediaTypes.HAL_JSON))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse();

            // Follow self link should succeed
            var firstSelfLink = objectMapper.readTree(firstPage.getContentAsString()).at("/_links/self/href").asText();
            assertThat(firstSelfLink).isNotBlank();
            mockMvc.perform(get(firstSelfLink)
                            .accept(MediaTypes.HAL_JSON))
                    .andExpect(status().isOk());

            var secondLink = objectMapper.readTree(firstPage.getContentAsString()).at("/_links/next/href").asText();
            assertThat(secondLink).isNotBlank();

            // Follow next link and check the results
            var secondPage = mockMvc.perform(get(secondLink)
                            .accept(MediaTypes.HAL_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$._embedded.item.length()", is(10)))
                    .andExpect(jsonPath("$._embedded.item[?(@.name=='product_11')].price", is(List.of(220.00))))
                    .andExpect(jsonPath("$._embedded.item[?(@.name=='product_12')].price", is(List.of(240.00))))

                    // Check pagination links
                    .andExpect(jsonPath("$._links.self.href", containsString("_cursor")))
                    .andExpect(jsonPath("$._links.self.href", containsString("_size=10")))
                    .andExpect(jsonPath("$._links.self.href", containsString("_sort=price,asc&_sort=name,desc")))
                    .andExpect(jsonPath("$._links.first.href", containsString("_cursor")))
                    .andExpect(jsonPath("$._links.first.href", containsString("_size=10")))
                    .andExpect(jsonPath("$._links.first.href", containsString("_sort=price,asc&_sort=name,desc")))
                    .andExpect(jsonPath("$._links.prev.href", containsString("_cursor")))
                    .andExpect(jsonPath("$._links.prev.href", containsString("_size=10")))
                    .andExpect(jsonPath("$._links.prev.href", containsString("_sort=price,asc&_sort=name,desc")))
                    .andExpect(jsonPath("$._links.next.href", containsString("_cursor")))
                    .andExpect(jsonPath("$._links.next.href", containsString("_size=10")))
                    .andExpect(jsonPath("$._links.next.href", containsString("_sort=price,asc&_sort=name,desc")))
                    .andExpect(jsonPath("$._links.last.href").doesNotExist())

                    // Check pagination metadata
                    .andExpect(jsonPath("$.page.size", is(10)))
                    .andExpect(jsonPath("$.page.prev_cursor").exists())
                    .andExpect(jsonPath("$.page.next_cursor").exists())
                    .andExpect(jsonPath("$.page.total_items_estimate", is(100)))
                    .andExpect(jsonPath("$.page.total_items_exact", is(100)))
                    // Check legacy properties don't exist
                    .andExpect(jsonPath("$.page.number").doesNotExist())
                    .andExpect(jsonPath("$.page.totalElements").doesNotExist())
                    .andExpect(jsonPath("$.page.totalPages").doesNotExist())

                    // return second page
                    .andReturn()
                    .getResponse();

            var thirdLink = objectMapper.readTree(secondPage.getContentAsString()).at("/_links/next/href").asText();
            assertThat(thirdLink).isNotBlank();

            // Follow next link of second page
            var thirdPage = mockMvc.perform(get(thirdLink)
                            .accept(MediaTypes.HAL_JSON))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse();

            // Check first and prev links
            var firstLink = objectMapper.readTree(thirdPage.getContentAsString()).at("/_links/first/href").asText();
            assertThat(firstLink).isEqualTo(firstSelfLink);
            var prevLink = objectMapper.readTree(thirdPage.getContentAsString()).at("/_links/prev/href").asText();
            assertThat(prevLink).isEqualTo(secondLink);
        }

        @ParameterizedTest
        @CsvSource({"0", "25"})
        void testListEntityInstances_withCounts(int exact) throws Exception {
            for (var i = 0; i < exact; i++) {
                createProduct(i);
            }
            mockMvc.perform(get("/products").accept(MediaTypes.HAL_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page.total_items_estimate", is(exact)))
                    .andExpect(jsonPath("$.page.total_items_exact", is(exact)));
        }

        @Test
        void testListEntityInstances_defaultPageSize() throws Exception {
            mockMvc.perform(get("/products").accept(MediaTypes.HAL_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page.size", is(20)));
        }

        @ParameterizedTest
        @CsvSource({"5,5", "100,100", "1000,1000"})
        void testListEntityInstances_pageSize(String requestedSize, int actualSize) throws Exception {
            mockMvc.perform(get("/products?_size={size}", requestedSize).accept(MediaTypes.HAL_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page.size", is(actualSize)));
        }

        @ParameterizedTest
        @CsvSource({"0", "-1", "-10", "abc", "1001", "10000"})
        void testListEntityInstances_pageSize_invalid(String requestedSize) throws Exception {
            createProduct(1);
            mockMvc.perform(get("/products?_size={size}", requestedSize).accept(MediaTypes.HAL_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value("https://contentgrid.cloud/problems/invalid-query-parameter/pagination"));
        }
    }

    @Nested
    class UpdateEntity {

        @ParameterizedTest
        @MethodSource("com.contentgrid.appserver.rest.EntityRestControllerTest#supportedMediaTypes")
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
                    .andExpect(status().isNoContent());
        }

        @ParameterizedTest
        @MethodSource("com.contentgrid.appserver.rest.EntityRestControllerTest#supportedMediaTypes")
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
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                            .withStatusCode(HttpStatus.NOT_FOUND)
                                    .withType("https://contentgrid.cloud/problems/not-found/entity-definition")
                                    .withTitle("Entity or resource not found")
                    );
        }

        @ParameterizedTest
        @MethodSource("com.contentgrid.appserver.rest.EntityRestControllerTest#supportedMediaTypes")
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
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                                    .withStatusCode(HttpStatus.NOT_FOUND)
                                    .withType("https://contentgrid.cloud/problems/not-found/entity-item")
                                    .withTitle("Entity item not found")

                    );
        }

        @Test
        void testUpdateCorrectIfMatch() throws Exception {
            var createResponse = createInvoice();

            var updateResponse = mockMvc.perform(patch(createResponse.getRedirectedUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("If-Match", createResponse.getHeader(HttpHeaders.ETAG))
                            .content("""
                                    {
                                        "number": "456",
                                        "amount": "123"
                                    }
                                    """)
                    )
                    .andExpect(status().isNoContent())
                    .andExpect(header().exists(HttpHeaders.ETAG))
                    .andReturn()
                    .getResponse();

            // ETag has changed
            assertThat(createResponse.getHeader(HttpHeaders.ETAG))
                    .isNotEqualTo(updateResponse.getHeader(HttpHeaders.ETAG));

            mockMvc.perform(get(createResponse.getRedirectedUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                    ).andExpect(status().isOk())
                    // ETag has not changed
                    .andExpect(header().string(HttpHeaders.ETAG, updateResponse.getHeader(HttpHeaders.ETAG)))
                    .andExpect(jsonPath("$.number").value("456"))
                    .andExpect(jsonPath("$.amount").value("123"));
        }

        @Test
        void testUpdateIncorrectIfMatch() throws Exception {
            var createResponse = createInvoice();

            mockMvc.perform(put(createResponse.getRedirectedUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("If-Match", "\"some-other-etag\"")
                            .content("""
                                    {
                                        "number": "456",
                                        "amount": "123"
                                    }
                                    """)
                    )
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                                    .withStatusCode(HttpStatus.PRECONDITION_FAILED)
                                    .withType("https://contentgrid.cloud/problems/unsatisfied-version")
                                    .withTitle("Object has changed")
                    );

            mockMvc.perform(get(createResponse.getRedirectedUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                    ).andExpect(status().isOk())
                    // ETag has not changed
                    .andExpect(header().string(HttpHeaders.ETAG, createResponse.getHeader(HttpHeaders.ETAG)))
                    .andExpect(jsonPath("$.number").value("123"))
                    .andExpect(jsonPath("$.amount").value("150"));
        }

        @Test
        void testUpdateInvalidIfMatch() throws Exception {
            var createResponse = createInvoice();

            mockMvc.perform(patch(createResponse.getRedirectedUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("If-Match", createResponse.getHeader(HttpHeaders.ETAG)
                                    // Emulate accidentally-invalid etag where quotes are omitted
                                    .replace('"', ' '))
                            .content("""
                                    {
                                        "number": "456",
                                        "amount": "123"
                                    }
                                    """)
                    )
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                                    .withStatusCode(HttpStatus.BAD_REQUEST)
                                    .withDetail("Invalid ETag in header")
                    );

            mockMvc.perform(get(createResponse.getRedirectedUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                    ).andExpect(status().isOk())
                    // ETag has not changed
                    .andExpect(header().string(HttpHeaders.ETAG, createResponse.getHeader(HttpHeaders.ETAG)))
                    .andExpect(jsonPath("$.number").value("123"))
                    .andExpect(jsonPath("$.amount").value("150"));

        }

        @Test
        void testUpdateIfMatchWithoutVersionedObject() throws Exception {
            var createResponse = createPerson();

            mockMvc.perform(patch(createResponse.getRedirectedUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("If-Match", "\"my-etag\"")
                            .content("""
                                    {
                                        "name": "new name"
                                    }
                                    """)
                    )
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                                    .withStatusCode(HttpStatus.PRECONDITION_FAILED)
                                    .withType("https://contentgrid.cloud/problems/unsatisfied-version")
                                    .withTitle("Object has changed")
                    );

            mockMvc.perform(get(createResponse.getRedirectedUrl()))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist(HttpHeaders.ETAG))
                    .andExpect(jsonPath("$.name").value("test"));
        }

        @Test
        void testUpdateAuditMetadata() throws Exception {
            var createResponse = createInvoice();

            mockMvc.perform(patch(createResponse.getRedirectedUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "number": "456", "amount": "123" }
                                    """)
                            .with(jwt().jwt(jwt -> jwt.subject("alice@example.com")
                                    .claim("name", "Alice")))
                    )
                    .andExpect(status().isNoContent());

            mockMvc.perform(get(createResponse.getRedirectedUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                    ).andExpect(status().isOk())
                    .andExpect(jsonPath("$.audit_metadata.created_by", is("user")))
                    .andExpect(jsonPath("$.audit_metadata.created_date", startsWith("2009-02-13")))
                    .andExpect(jsonPath("$.audit_metadata.last_modified_by", is("Alice")))
                    .andExpect(jsonPath("$.audit_metadata.last_modified_date", startsWith("2009-02-13")))
            ;
        }

        @Test
        void testUpdateContentFilenameAndMimetype_http200() throws Exception {
            // create product with content
            var productResponse = mockMvc.perform(multipart("/products")
                            .file(new MockMultipartFile("picture", "IMG_456.jpg", "application/jpeg",
                                    InputStream.nullInputStream()))
                            .param("name", "My product")
                            .param("price", "120"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.picture.filename", is("IMG_456.jpg")))
                    .andExpect(jsonPath("$.picture.mimetype", is("application/jpeg")))
                    .andExpect(jsonPath("$.picture.length", is(0)))
                    .andReturn()
                    .getResponse();

            // Update product
            mockMvc.perform(put(productResponse.getRedirectedUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "name": "My product",
                              "price": 120,
                              "picture": {
                                "filename": "IMG_789.png",
                                "mimetype": "application/png"
                              }
                            }
                            """))
                    .andExpect(status().isNoContent());
        }

        @Test
        void testUpdateMissingContentFilename_http200() throws Exception {
            // create product with content
            var productResponse = mockMvc.perform(multipart("/products")
                            .file(new MockMultipartFile("picture", "IMG_456.jpg", "application/jpeg",
                                    InputStream.nullInputStream()))
                            .param("name", "My product")
                            .param("price", "120"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.picture.filename", is("IMG_456.jpg")))
                    .andExpect(jsonPath("$.picture.mimetype", is("application/jpeg")))
                    .andExpect(jsonPath("$.picture.length", is(0)))
                    .andReturn()
                    .getResponse();

            // Update product
            mockMvc.perform(put(productResponse.getRedirectedUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                            {
                              "name": "My product",
                              "price": 120,
                              "picture": {
                                "mimetype": "application/png"
                              }
                            }
                            """))
                    .andExpect(status().isNoContent());
        }

        static Stream<Arguments> testUpdateMissingContentMimetype_http400() {
            return Stream.of(
                    Arguments.argumentSet("Mimetype missing", """
                            {
                              "name": "My product",
                              "price": 120,
                              "picture": {
                                "filename": "IMG_789.png"
                              }
                            }
                            """), Arguments.argumentSet("Mimetype null", """
                            {
                              "name": "My product",
                              "price": 120,
                              "picture": {
                                "filename": "IMG_789.png",
                                "mimetype": null
                              }
                            }
                            """), Arguments.argumentSet("Empty content", """
                            {
                              "name": "My product",
                              "price": 120,
                              "picture": {}
                            }
                            """), Arguments.argumentSet("Content null", """
                            {
                              "name": "My product",
                              "price": 120,
                              "picture": null
                            }
                            """), Arguments.argumentSet("Content missing", """
                            {
                              "name": "My product",
                              "price": 120
                            }
                            """)
            );
        }

        @ParameterizedTest
        @MethodSource
        void testUpdateMissingContentMimetype_http400(String body) throws Exception {
            // create product with content
            var productResponse = mockMvc.perform(multipart("/products")
                            .file(new MockMultipartFile("picture", "IMG_456.jpg", "application/jpeg",
                                    InputStream.nullInputStream()))
                            .param("name", "My product")
                            .param("price", "120"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.picture.filename", is("IMG_456.jpg")))
                    .andExpect(jsonPath("$.picture.mimetype", is("application/jpeg")))
                    .andExpect(jsonPath("$.picture.length", is(0)))
                    .andReturn()
                    .getResponse();

            // Update product
            mockMvc.perform(put(productResponse.getRedirectedUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(ProblemDetailsMockMvcMatchers.validationConstraintViolation()
                            .withError(e -> e.withType("https://contentgrid.cloud/problems/input/validation/required")
                                    .withTitle("Mandatory field")
                                    .withDetail("A value must be present, but it is missing or empty")
                                    .withProperty("picture.mimetype")
                            ));
        }

        @Test
        void testUpdateContentFilenameAndMimetype_missingContent_http400() throws Exception {
            // Create valid product without picture
            var productResponse = mockMvc.perform(multipart("/products")
                            .param("name", "My product")
                            .param("price", "120"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.picture.length").doesNotExist())
                    .andReturn()
                    .getResponse();

            // Update product
            mockMvc.perform(put(productResponse.getRedirectedUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                            {
                              "name": "My product",
                              "price": 120,
                              "picture": {
                                "filename": "IMG_789.png",
                                "mimetype": "application/png"
                              }
                            }
                            """))
                    .andExpect(ProblemDetailsMockMvcMatchers.validationConstraintViolation()
                            .withError(e -> e.withType("https://contentgrid.cloud/problems/input/validation/no-content")
                                    .withTitle("No content present")
                                    .withDetail("Content attributes can not be set when there is no content present")
                                    .withProperty("picture")
                            )
                    );
        }

    }

    @Nested
    class DeleteEntity {

        @Test
        void testSuccessfullyDeleteEntityInstance() throws Exception {
            // First create an entity
            Map<String, Object> product = new HashMap<>();
            product.put("name", "Product to Delete");
            product.put("price", 79.99);
            product.put("release_date", "2023-03-15T12:00:00Z");
            product.put("in_stock", true);

            String responseContent = mockMvc.perform(post("/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(product)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            // Extract ID from created entity
            String id = objectMapper.readTree(responseContent).get("id").asText();

            // Delete the entity
            mockMvc.perform(delete("/products/" + id))
                    .andExpect(status().isNoContent());

            // Verify entity no longer exists
            mockMvc.perform(get("/products/" + id))
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                            .withStatusCode(HttpStatus.NOT_FOUND)
                            .withType("https://contentgrid.cloud/problems/not-found/entity-item")
                            .withTitle("Entity item not found")
                            .withField("id", id)
                    );
        }

        @Test
        void testDeleteNonExistentEntityInstance() throws Exception {
            String nonExistentId = UUID.randomUUID().toString();

            mockMvc.perform(delete("/products/" + nonExistentId))
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                            .withStatusCode(HttpStatus.NOT_FOUND)
                            .withType("https://contentgrid.cloud/problems/not-found/entity-item")
                            .withTitle("Entity item not found")
                            .withField("id", nonExistentId)
                    );
        }

        @Test
        void testDeleteNonExistentEntityType() throws Exception {
            String someId = UUID.randomUUID().toString();

            mockMvc.perform(delete("/foobars/" + someId))
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                            .withStatusCode(HttpStatus.NOT_FOUND)
                            .withType("https://contentgrid.cloud/problems/not-found/entity-definition")
                            .withTitle("Entity or resource not found")
                    );
        }

        @Test
        void testDeleteCorrectIfMatch() throws Exception {
            var createResponse = createInvoice();

            mockMvc.perform(delete(createResponse.getRedirectedUrl())
                            .header("If-Match", createResponse.getHeader(HttpHeaders.ETAG)))
                    .andExpect(status().isNoContent())
                    .andReturn()
                    .getResponse();

            // Verify entity was deleted
            mockMvc.perform(get(createResponse.getRedirectedUrl()))
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                                    .withStatusCode(HttpStatus.NOT_FOUND)
                                    .withType("https://contentgrid.cloud/problems/not-found/entity-item")
                                    .withTitle("Entity item not found")
                    );
        }

        @Test
        void testDeleteIncorrectIfMatch() throws Exception {
            var createResponse = createInvoice();

            mockMvc.perform(delete(createResponse.getRedirectedUrl())
                            .header("If-Match", "\"some-other-etag\"")
                    )
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                                    .withStatusCode(HttpStatus.PRECONDITION_FAILED)
                                    .withType("https://contentgrid.cloud/problems/unsatisfied-version")
                                    .withTitle("Object has changed")
                    );

            // Verify entity still exists
            mockMvc.perform(get(createResponse.getRedirectedUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                    ).andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ETAG, createResponse.getHeader(HttpHeaders.ETAG)))
                    .andExpect(jsonPath("$.number").value("123"))
                    .andExpect(jsonPath("$.amount").value("150"));
        }

        @Test
        void testDeleteInvalidIfMatch() throws Exception {
            var createResponse = createInvoice();

            mockMvc.perform(delete(createResponse.getRedirectedUrl())
                            .header("If-Match", createResponse.getHeader(HttpHeaders.ETAG)
                                    // Emulate accidentally-invalid etag where quotes are omitted
                                    .replace('"', ' '))
                    )
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                                    .withStatusCode(HttpStatus.BAD_REQUEST)
                                    .withDetail("Invalid ETag in header")
                    );

            // Verify entity still exists
            mockMvc.perform(get(createResponse.getRedirectedUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                    ).andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ETAG, createResponse.getHeader(HttpHeaders.ETAG)))
                    .andExpect(jsonPath("$.number").value("123"))
                    .andExpect(jsonPath("$.amount").value("150"));
        }
    }

}
