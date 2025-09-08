package com.contentgrid.appserver.rest;


import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.APPLICATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.contentgrid.appserver.query.engine.api.TableCreator;
import com.contentgrid.appserver.registry.SingleApplicationResolver;
import com.contentgrid.thunx.encoding.json.JsonThunkExpressionCoder;
import com.contentgrid.thunx.predicates.model.Comparison;
import com.contentgrid.thunx.predicates.model.Scalar;
import com.contentgrid.thunx.predicates.model.SymbolicReference;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.Arguments.ArgumentSet;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PermissionsPropagationTest {
    @Autowired
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        public SingleApplicationResolver singleApplicationResolver() {
            return new SingleApplicationResolver(APPLICATION);
        }
    }

    static String encodeThunk(ThunkExpression<Boolean> thunk) {
        var data = new JsonThunkExpressionCoder().encode(thunk);
        return Base64.getEncoder().encodeToString(data);
    }

    private static final BigDecimal AMOUNT_THRESHOLD_ALWAYS_ALLOWED = BigDecimal.valueOf(100);
    private static final BigDecimal AMOUNT_THRESHOLD_FOR_TEST = AMOUNT_THRESHOLD_ALWAYS_ALLOWED.min(BigDecimal.ONE);


    static Stream<Arguments> permissionHeaders() {
        return Stream.of(
                Arguments.argumentSet("has permission always", encodeThunk(Scalar.of(true)), true),
                Arguments.argumentSet("has permission never", encodeThunk(Scalar.of(false)), false),
                Arguments.argumentSet("has permission based on amount (passing)", encodeThunk(Comparison.lessOrEquals(
                        SymbolicReference.parse("entity.amount"),
                        Scalar.of(AMOUNT_THRESHOLD_ALWAYS_ALLOWED.longValue())
                )), true),
                Arguments.argumentSet("has permission based on amount (failing)", encodeThunk(Comparison.greaterOrEquals(
                        SymbolicReference.parse("entity.amount"),
                        Scalar.of(AMOUNT_THRESHOLD_ALWAYS_ALLOWED.longValue())
                )), false)
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

    private String createPerson() throws Exception {
        return mockMvc.perform(post("/persons")
                        .header("X-ABAC-Context", encodeThunk(Scalar.of(true)))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Test person")
                        .param("vat", UUID.randomUUID().toString())
                ).andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getRedirectedUrl();
    }

    private String createProduct() throws Exception {
        return mockMvc.perform(post("/products")
                        .header("X-ABAC-Context", encodeThunk(Scalar.of(true)))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Test product")
                        .param("price", "123")
                ).andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getRedirectedUrl();
    }

    private String createInvoice(BigDecimal amount) throws Exception {
        return mockMvc.perform(multipart("/invoices")
                        .file(new MockMultipartFile("content", "my-file.pdf", "application/pdf", new byte[12]))
                        .header("X-ABAC-Context", encodeThunk(Scalar.of(true)))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("number", UUID.randomUUID().toString())
                        .param("amount", amount.toPlainString())
                        .param("confidentiality", "public")
                        .param("customer", createPerson())
                        .param("products", createProduct())
                        .param("products", createProduct())
                ).andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getRedirectedUrl();
    }

    @ParameterizedTest
    @MethodSource("permissionHeaders")
    void listEntity(String abacContext, boolean isAllowed) throws Exception {
        createInvoice(AMOUNT_THRESHOLD_FOR_TEST);

        mockMvc.perform(get("/invoices")
                .header("X-ABAC-Context", abacContext)
        ).andExpect(status().isOk())
                .andExpect(result -> {
                    var response = objectMapper.readTree(result.getResponse().getContentAsString());
                    assertThat((Object)response.path("_embedded").path("item"))
                            .satisfies(obj -> {
                                if(isAllowed) {
                                    assertThat(obj).isInstanceOfSatisfying(ArrayNode.class, arr -> {
                                        assertThat(arr).hasSize(1);
                                    });
                                } else {
                                    assertThat(obj)
                                            .satisfiesAnyOf(missingNode -> {
                                                        assertThat(missingNode).isInstanceOf(MissingNode.class);
                                                    },
                                                    arrayNode -> {
                                                        assertThat(arrayNode).isInstanceOfSatisfying(ArrayNode.class, arr -> {
                                                            assertThat(arr).isEmpty();
                                                        });
                                                    }
                                            );
                                }
                            });
                });
    }

    @ParameterizedTest
    @MethodSource("permissionHeaders")
    void getEntity(String abacContext, boolean isAllowed) throws Exception {
        mockMvc.perform(get(createInvoice(AMOUNT_THRESHOLD_FOR_TEST))
                .header("X-ABAC-Context", abacContext)
        ).andExpect(isAllowed?status().isOk():status().isForbidden());
    }

    @ParameterizedTest
    @MethodSource("permissionHeaders")
    void createEntity(String abacContext, boolean isAllowed) throws Exception {
        mockMvc.perform(post("/invoices")
                .header("X-ABAC-Context", abacContext)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("number", UUID.randomUUID().toString())
                .param("amount", AMOUNT_THRESHOLD_FOR_TEST.toPlainString())
                .param("confidentiality", "public")
                .param("customer", createPerson())
        ).andExpect(isAllowed ? status().isCreated():status().isForbidden());
    }

    static Stream<Arguments> updatePermissionHeaders() {
        return Stream.of(HttpMethod.PUT, HttpMethod.PATCH)
                .flatMap(method -> permissionHeaders()
                .map(ArgumentSet.class::cast)
                .map(args -> {
                    var newArgs = Stream.concat(
                            Stream.of(method),
                            Arrays.stream(args.get())
                    ).toArray();
                    return Arguments.argumentSet(args.getName()+" ["+method+"]", newArgs);
                })
        );
    }

    @ParameterizedTest
    @MethodSource("updatePermissionHeaders")
    void updateEntityExisting(HttpMethod method, String abacContext, boolean isAllowed) throws Exception {
        // Existing value is varying; new value is allowed
        var invoice = createInvoice(AMOUNT_THRESHOLD_FOR_TEST);

        var content = Map.of(
                "number", UUID.randomUUID().toString(),
                "amount", AMOUNT_THRESHOLD_ALWAYS_ALLOWED.toPlainString(),
                "confidentiality", "public"
        );

        mockMvc.perform(request(method, invoice)
                .header("X-ABAC-Context", abacContext)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(content))
        ).andExpect(isAllowed ? status().isOk():status().isForbidden());
    }

    @ParameterizedTest
    @MethodSource("updatePermissionHeaders")
    void updateEntityNew(HttpMethod method, String abacContext, boolean isAllowed) throws Exception {
        // Existing value is allowed; new value is varying
        // Skip the "never permission", because that already forbids the existing value, and is covered by the test above
        assumeThat(ThunkExpression.maybeValue(new JsonThunkExpressionCoder().decode(Base64.getDecoder().decode(abacContext))))
                .isNotEqualTo(Optional.of(false));

        var invoice = createInvoice(AMOUNT_THRESHOLD_ALWAYS_ALLOWED);

        var content = Map.of(
                "number", UUID.randomUUID().toString(),
                "amount", AMOUNT_THRESHOLD_FOR_TEST.toPlainString(),
                "confidentiality", "public"
        );

        mockMvc.perform(request(method, invoice)
                .header("X-ABAC-Context", abacContext)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(content))
        ).andExpect(isAllowed ? status().isOk():status().isForbidden());
    }

    @ParameterizedTest
    @MethodSource("permissionHeaders")
    void deleteEntity(String abacContext, boolean isAllowed) throws Exception {
        var invoice = createInvoice(AMOUNT_THRESHOLD_FOR_TEST);

        mockMvc.perform(delete(invoice)
                        .header("X-ABAC-Context", abacContext)
                )
                .andExpect(isAllowed?status().isOk():status().isForbidden());
    }

    @ParameterizedTest
    @MethodSource("permissionHeaders")
    void getContent(String abacContext, boolean isAllowed) throws Exception {
        var invoice = createInvoice(AMOUNT_THRESHOLD_FOR_TEST);

        mockMvc.perform(get(invoice+"/content")
                .header("X-ABAC-Context", abacContext)
        ).andExpect(isAllowed?status().isOk():status().isForbidden());
    }

    @ParameterizedTest
    @MethodSource("permissionHeaders")
    void headContent(String abacContext, boolean isAllowed) throws Exception {
        var invoice = createInvoice(AMOUNT_THRESHOLD_FOR_TEST);

        mockMvc.perform(request(HttpMethod.HEAD, invoice+"/content")
                .header("X-ABAC-Context", abacContext)
        ).andExpect(isAllowed?status().isOk():status().isForbidden());
    }

    @ParameterizedTest
    @MethodSource("permissionHeaders")
    void updateContentStream(String abacContext, boolean isAllowed) throws Exception {
        var invoice = createInvoice(AMOUNT_THRESHOLD_FOR_TEST);

        mockMvc.perform(post(invoice+"/content")
                .header("X-ABAC-Context", abacContext)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(new byte[150])
        ).andExpect(isAllowed?status().isNoContent():status().isForbidden());
    }

    @ParameterizedTest
    @MethodSource("permissionHeaders")
    void updateContentMultipart(String abacContext, boolean isAllowed) throws Exception {
        var invoice = createInvoice(AMOUNT_THRESHOLD_FOR_TEST);

        mockMvc.perform(multipart(invoice+"/content")
                .file(new MockMultipartFile("file", "new-file.pdf", "application/pdf", new byte[14]))
                .header("X-ABAC-Context", abacContext)
        ).andExpect(isAllowed?status().isNoContent():status().isForbidden());
    }

    @ParameterizedTest
    @MethodSource("permissionHeaders")
    void deleteContent(String abacContext, boolean isAllowed) throws Exception {
        var invoice = createInvoice(AMOUNT_THRESHOLD_FOR_TEST);

        mockMvc.perform(delete(invoice+"/content")
                .header("X-ABAC-Context", abacContext)
        ).andExpect(isAllowed?status().isNoContent():status().isForbidden());

    }

    @ParameterizedTest
    @MethodSource("permissionHeaders")
    void getOneRelation(String abacContext, boolean isAllowed) throws Exception {
        var invoice = createInvoice(AMOUNT_THRESHOLD_FOR_TEST);

        mockMvc.perform(get(invoice+"/customer")
                        .header("X-ABAC-Context", abacContext)
                )
                .andExpect(isAllowed?status().isFound():status().isForbidden());
    }

    @ParameterizedTest
    @MethodSource("permissionHeaders")
    void updateOneRelation(String abacContext, boolean isAllowed) throws Exception {
        var invoice = createInvoice(AMOUNT_THRESHOLD_FOR_TEST);

        var person = createPerson();

        mockMvc.perform(put(invoice+"/customer")
                        .header("X-ABAC-Context", abacContext)
                        .contentType("text/uri-list")
                        .content(person)
                )
                .andExpect(isAllowed?status().isNoContent():status().isForbidden());
    }

    @ParameterizedTest
    @MethodSource("permissionHeaders")
    void deleteOneRelation(String abacContext, boolean isAllowed) throws Exception {
        var invoice = createInvoice(AMOUNT_THRESHOLD_FOR_TEST);

        mockMvc.perform(delete(invoice+"/previous-invoice")
                        .header("X-ABAC-Context", abacContext)
                )
                .andExpect(isAllowed?status().isNoContent():status().isForbidden());
    }

    @ParameterizedTest
    @MethodSource("permissionHeaders")
    void getManyRelation(String abacContext, boolean isAllowed) throws Exception {
        var invoice = createInvoice(AMOUNT_THRESHOLD_FOR_TEST);

        mockMvc.perform(get(invoice+"/products")
                        .header("X-ABAC-Context", abacContext)
                )
                .andExpect(isAllowed?status().isFound():status().isForbidden());
    }

    @ParameterizedTest
    @MethodSource("permissionHeaders")
    void updateManyRelation(String abacContext, boolean isAllowed) throws Exception {
        var invoice = createInvoice(AMOUNT_THRESHOLD_FOR_TEST);

        var product = createProduct();

        mockMvc.perform(post(invoice+"/products")
                        .header("X-ABAC-Context", abacContext)
                        .contentType("text/uri-list")
                        .content(product)
                )
                .andExpect(isAllowed?status().isNoContent():status().isForbidden());
    }

    @ParameterizedTest
    @MethodSource("permissionHeaders")
    void getManyRelationItem(String abacContext, boolean isAllowed) throws Exception {
        var invoice = createInvoice(AMOUNT_THRESHOLD_FOR_TEST);

        var productsUrl = mockMvc.perform(get(invoice+"/products")
                .header("X-ABAC-Context", encodeThunk(Scalar.of(true)))
                .accept(MediaType.APPLICATION_JSON)
        ).andExpect(status().isFound())
                .andReturn()
                .getResponse()
                .getRedirectedUrl();

        var productId = objectMapper.readTree(
                mockMvc.perform(get(productsUrl)
                                .header("X-ABAC-Context", encodeThunk(Scalar.of(true)))
                                .accept(MediaType.APPLICATION_JSON)
                        ).andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        ).get("_embedded").get("item").get(0).get("id").require().textValue();

        mockMvc.perform(get(invoice+"/products/"+productId)
                .header("X-ABAC-Context", abacContext)
        ).andExpect(isAllowed?status().isFound():status().isForbidden());
    }

    @ParameterizedTest
    @MethodSource("permissionHeaders")
    void deleteManyRelationItem(String abacContext, boolean isAllowed) throws Exception {
        var invoice = createInvoice(AMOUNT_THRESHOLD_FOR_TEST);

        var productsUrl = mockMvc.perform(get(invoice+"/products")
                        .header("X-ABAC-Context", encodeThunk(Scalar.of(true)))
                        .accept(MediaType.APPLICATION_JSON)
                ).andExpect(status().isFound())
                .andReturn()
                .getResponse()
                .getRedirectedUrl();

        var productId = objectMapper.readTree(
                mockMvc.perform(get(productsUrl)
                                .header("X-ABAC-Context", encodeThunk(Scalar.of(true)))
                                .accept(MediaType.APPLICATION_JSON)
                        ).andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        ).get("_embedded").get("item").get(0).get("id").require().textValue();

        mockMvc.perform(delete(invoice+"/products/"+productId)
                .header("X-ABAC-Context", abacContext)
        ).andExpect(isAllowed?status().isNoContent():status().isForbidden());
    }
}
