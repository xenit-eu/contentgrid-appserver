package com.contentgrid.appserver.rest;

import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
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

import com.contentgrid.appserver.domain.DatamodelApi;
import com.contentgrid.appserver.domain.data.DataEntry.FileDataEntry;
import com.contentgrid.appserver.domain.paging.ItemCount;
import com.contentgrid.appserver.domain.paging.ResultSlice;
import com.contentgrid.appserver.domain.paging.cursor.EncodedCursorPagination;
import com.contentgrid.appserver.domain.paging.cursor.EncodedCursorPaginationControls;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.query.engine.api.TableCreator;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.SimpleAttributeData;
import com.contentgrid.appserver.registry.SingleApplicationResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.LinkedMultiValueMap;

@SpringBootTest(properties = "contentgrid.thunx.abac.source=none")
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

    @MockitoSpyBean
    DatamodelApi datamodelApi;

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
                .andExpect(header().doesNotExist(HttpHeaders.ETAG))
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
                        .file(new MockMultipartFile("picture", "IMG_456.jpg", "application/jpeg", InputStream.nullInputStream()))
                        .param("name", "My product")
                        .param("price", "120")
                ).andExpect(status().isCreated())
                .andExpect(header().doesNotExist(HttpHeaders.ETAG))
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
                .andExpect(jsonPath("$.detail", is("Invalid property data at picture: Invalid format for type CONTENT: Content-Type is required")))
        ;
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
                .andExpect(header().doesNotExist(HttpHeaders.ETAG))
                .andReturn().getResponse().getContentAsString();

        // Extract ID from created entity
        String id = objectMapper.readTree(responseContent).get("id").asText();

        // Then retrieve it with application/hal+json
        mockMvc.perform(get("/products/" + id).accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.ETAG))
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
                .andExpect(header().doesNotExist(HttpHeaders.ETAG))
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
                .andExpect(header().doesNotExist(HttpHeaders.ETAG))
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
                .andExpect(jsonPath("$._links.self.href").exists())
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
                .andExpect(jsonPath("$._links.curies").isArray())
                .andExpect(jsonPath("$.page").exists());

        // Test the list endpoint with application/prs.hal-forms+json
        mockMvc.perform(get("/products")
                        .accept(MediaTypes.HAL_FORMS_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaTypes.HAL_FORMS_JSON))
                .andExpect(jsonPath("$._links.self.href").exists())
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
                .andExpect(jsonPath("$._links.curies").isArray())
                .andExpect(jsonPath("$.page").exists());
    }

    @Test
    void testListEntityInstances_withPaging() throws Exception {
        ArgumentCaptor<EncodedCursorPagination> paginationArg = ArgumentCaptor.forClass(EncodedCursorPagination.class);
        Mockito.doAnswer(invocation -> fakeProducts(paginationArg.getValue()))
                .when(datamodelApi)
                .findAll(Mockito.eq(APPLICATION), Mockito.eq(PRODUCT), Mockito.any(), paginationArg.capture(), Mockito.any());

        // Request default page
        mockMvc.perform(get("/products")
                        .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.item.length()", is(20)))
                .andExpect(jsonPath("$._embedded.item[?(@.name=='product_1')].price", is(List.of(20.00))))
                .andExpect(jsonPath("$._embedded.item[?(@.name=='product_2')].price", is(List.of(40.00))))

                // Check pagination links
                .andExpect(jsonPath("$._links.self.href").exists())
                .andExpect(jsonPath("$._links.first.href").exists())
                .andExpect(jsonPath("$._links.prev.href").doesNotExist())
                .andExpect(jsonPath("$._links.next.href").exists())
                .andExpect(jsonPath("$._links.last.href").doesNotExist())

                // Check pagination metadata
                .andExpect(jsonPath("$.page.size", is(20)))
                .andExpect(jsonPath("$.page.prev_cursor").doesNotExist())
                .andExpect(jsonPath("$.page.next_cursor", is("1")))
                .andExpect(jsonPath("$.page.total_items_estimate", is(1_000_000)))
                .andExpect(jsonPath("$.page.total_items_exact", is(1_000_000)))
                // Check legacy properties don't exist
                .andExpect(jsonPath("$.page.number").doesNotExist())
                .andExpect(jsonPath("$.page.totalElements").doesNotExist())
                .andExpect(jsonPath("$.page.totalPages").doesNotExist());


        // Request with all paging parameters present
        mockMvc.perform(get("/products?_cursor=2&_size=10&_sort=price,asc&_sort=name,desc")
                        .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.item.length()", is(10)))
                .andExpect(jsonPath("$._embedded.item[?(@.name=='product_21')].price", is(List.of(420.00))))
                .andExpect(jsonPath("$._embedded.item[?(@.name=='product_22')].price", is(List.of(440.00))))

                // Check pagination links
                .andExpect(jsonPath("$._links.self.href", containsString("_cursor=2")))
                .andExpect(jsonPath("$._links.self.href", containsString("_size=10")))
                .andExpect(jsonPath("$._links.self.href", containsString("_sort=price,asc&_sort=name,desc")))
                .andExpect(jsonPath("$._links.first.href", not(containsString("_cursor"))))
                .andExpect(jsonPath("$._links.first.href", containsString("_size=10")))
                .andExpect(jsonPath("$._links.first.href", containsString("_sort=price,asc&_sort=name,desc")))
                .andExpect(jsonPath("$._links.prev.href", containsString("_cursor=1")))
                .andExpect(jsonPath("$._links.prev.href", containsString("_size=10")))
                .andExpect(jsonPath("$._links.prev.href", containsString("_sort=price,asc&_sort=name,desc")))
                .andExpect(jsonPath("$._links.next.href", containsString("_cursor=3")))
                .andExpect(jsonPath("$._links.next.href", containsString("_size=10")))
                .andExpect(jsonPath("$._links.next.href", containsString("_sort=price,asc&_sort=name,desc")))
                .andExpect(jsonPath("$._links.last.href").doesNotExist())

                // Check pagination metadata
                .andExpect(jsonPath("$.page.size", is(10)))
                .andExpect(jsonPath("$.page.prev_cursor", is("1")))
                .andExpect(jsonPath("$.page.next_cursor", is("3")))
                .andExpect(jsonPath("$.page.total_items_estimate", is(1_000_000)))
                .andExpect(jsonPath("$.page.total_items_exact", is(1_000_000)))
                // Check legacy properties don't exist
                .andExpect(jsonPath("$.page.number").doesNotExist())
                .andExpect(jsonPath("$.page.totalElements").doesNotExist())
                .andExpect(jsonPath("$.page.totalPages").doesNotExist());

        // Special case: second page, previous cursor is null
        mockMvc.perform(get("/products?_cursor=1").accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.first.href").exists())
                .andExpect(jsonPath("$._links.first.href", not(containsString("_cursor"))))
                .andExpect(jsonPath("$._links.prev.href").exists())
                .andExpect(jsonPath("$._links.prev.href", not(containsString("_cursor"))))
                .andExpect(jsonPath("$.page.prev_cursor", nullValue()));
    }

    @ParameterizedTest
    @CsvSource({
            "25,-1",
            "25,40",
            "25,21",
    })
    void testListEntityInstances_withCounts(long exact, long estimated) throws Exception {
        var itemCount = estimated < 0 ? ItemCount.exact(exact) : ItemCount.estimated(estimated);
        ArgumentCaptor<EncodedCursorPagination> paginationArg = ArgumentCaptor.forClass(EncodedCursorPagination.class);
        Mockito.doAnswer(invocation -> fakeProducts(paginationArg.getValue(), exact, itemCount))
                .when(datamodelApi)
                .findAll(Mockito.eq(APPLICATION), Mockito.eq(PRODUCT), Mockito.any(), paginationArg.capture(), Mockito.any());

        mockMvc.perform(get("/products").accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total_items_estimate", is((int) itemCount.count())))
                .andExpect(itemCount.isEstimated()
                        ? jsonPath("$.page.total_items_exact").doesNotExist()
                        : jsonPath("$.page.total_items_exact", is((int) itemCount.count())));
    }

    private static ResultSlice fakeProducts(EncodedCursorPagination pagination) {
        return fakeProducts(pagination, 1_000_000, ItemCount.exact(1_000_000));
    }

    private static ResultSlice fakeProducts(EncodedCursorPagination pagination, long count, ItemCount itemCount) {
        var sortData = pagination.getSort();
        var page = pagination.getCursor() == null ? 0 : Integer.parseInt(pagination.getCursor());
        var size = pagination.getSize();
        var entities = Stream.iterate(1, i -> i <= count, i -> i + 1)
                .skip((long) page * size)
                .limit(size)
                .map(EntityRestControllerTest::fakeProduct)
                .toList();

        var controls = new EncodedCursorPaginationControls(
                new EncodedCursorPagination(fakeCursor(page), size, sortData),
                ((long) (page + 1) * size) >= count ? null : new EncodedCursorPagination(fakeCursor(page + 1), size, sortData),
                page == 0 ? null : new EncodedCursorPagination(fakeCursor(page - 1), size, sortData),
                new EncodedCursorPagination(fakeCursor(0), size, sortData)
        );
        return new ResultSlice(entities, controls, itemCount);
    }

    private static EntityData fakeProduct(int i) {
        return EntityData.builder()
                .name(PRODUCT.getName())
                .id(fakeId(i))
                .attribute(SimpleAttributeData.builder()
                        .name(PRODUCT_NAME.getName())
                        .value("product_" + i)
                        .build())
                .attribute(SimpleAttributeData.builder()
                        .name(PRODUCT_PRICE.getName())
                        .value(i * 20.0)
                        .build())
                .attribute(SimpleAttributeData.builder()
                        .name(PRODUCT_IN_STOCK.getName())
                        .value(i % 2 == 1)
                        .build())
                .build();
    }

    private static EntityId fakeId(int i) {
        var hex = Integer.toHexString(i);
        var value = "00000000-0000-0000-0000-0000" + "0".repeat(8 - hex.length()) + hex;
        return EntityId.of(UUID.fromString(value));
    }

    private static String fakeCursor(int page) {
        return page <= 0 ? null : String.valueOf(page);
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
        var personCreate =  createPerson()
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
                .andExpect(status().isOk())
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
                .andExpect(status().isPreconditionFailed());

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
                .andExpect(status().isBadRequest());

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
                .andExpect(status().isPreconditionFailed());

        mockMvc.perform(get(createResponse.getRedirectedUrl()))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.ETAG))
                .andExpect(jsonPath("$.name").value("test"));
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
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://contentgrid.cloud/problems/invalid-query-parameter/sort"))
                .andExpect(jsonPath("$.detail").value(containsString("not found")));
    }

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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Product to Delete"));

        // Verify entity no longer exists
        mockMvc.perform(get("/products/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDeleteNonExistentEntityInstance() throws Exception {
        String nonExistentId = UUID.randomUUID().toString();

        mockMvc.perform(delete("/products/" + nonExistentId))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDeleteNonExistentEntityType() throws Exception {
        String someId = UUID.randomUUID().toString();

        mockMvc.perform(delete("/foobars/" + someId))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDeleteCorrectIfMatch() throws Exception {
        var createResponse = createInvoice();

        mockMvc.perform(delete(createResponse.getRedirectedUrl())
                        .header("If-Match", createResponse.getHeader(HttpHeaders.ETAG)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();

        // Verify entity was deleted
        mockMvc.perform(get(createResponse.getRedirectedUrl()))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDeleteIncorrectIfMatch() throws Exception {
        var createResponse = createInvoice();

        mockMvc.perform(delete(createResponse.getRedirectedUrl())
                        .header("If-Match", "\"some-other-etag\"")
                )
                .andExpect(status().isPreconditionFailed());

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
                .andExpect(status().isBadRequest());

        // Verify entity still exists
        mockMvc.perform(get(createResponse.getRedirectedUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, createResponse.getHeader(HttpHeaders.ETAG)))
                .andExpect(jsonPath("$.number").value("123"))
                .andExpect(jsonPath("$.amount").value("150"));
    }

}