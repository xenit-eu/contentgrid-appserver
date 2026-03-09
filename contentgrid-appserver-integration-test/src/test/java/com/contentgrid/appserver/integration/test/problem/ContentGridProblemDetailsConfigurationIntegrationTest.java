package com.contentgrid.appserver.integration.test.problem;

import static com.contentgrid.appserver.rest.test.ProblemDetailsMockMvcMatchers.problemDetails;
import static com.contentgrid.appserver.rest.test.ProblemDetailsMockMvcMatchers.validationConstraintViolation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.integration.test.fixture.invoicing.InvoicingApi;
import com.contentgrid.appserver.integration.test.fixture.invoicing.InvoicingApiApplication;
import com.contentgrid.appserver.rest.test.WithMockJwt;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

/**
 * All cases to be covered - Invalid json (no domain object can be constructed) - Validator violations (domain object
 * constructed; required attribute/relation missing) - Deletion violations (on delete object is still referenced by
 * required relation) - Database constraint errors (non-validation covered constraints)
 */
@SpringBootTest(properties = {
        "contentgrid.events.rabbitmq.enabled=false",
}, classes = {
        InvoicingApiApplication.class,
})
@AutoConfigureMockMvc(printOnlyOnFailure = false)
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
@WithMockJwt
class ContentGridProblemDetailsConfigurationIntegrationTest {

    private static final String PROBLEM_TYPE_PREFIX = "https://contentgrid.cloud/problems/";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    InvoicingApi invoicingApi;

    private EntityId createCustomer() throws InvalidPropertyDataException {
        return invoicingApi.createCustomer(null, "vat-" + UUID.randomUUID()).getIdentity().getEntityId();
    }

    private EntityId createInvoice() throws InvalidPropertyDataException {
        return invoicingApi.createInvoice("invoice-" + UUID.randomUUID(), false, false, createCustomer(), null)
                .getIdentity().getEntityId();
    }

    /**
     * Tests all invalid json input cases:
     *
     * <ul>
     * <li>Does not parse as json
     * <li>Type mismatch: trying to use a string for a number
     * <li>Type mismatch: trying to use a string for an object
     * <li>Type mismatch: string does not parse to a date
     * <li>to-one relation input: not a valid URL
     * <li>to-one relation input: URL to a different entity
     * </ul>
     */
    @Nested
    class InvalidJson {

        public static String CUSTOMER_ID_UPDATE;
        public static String INVOICE_ID_UPDATE;

        @BeforeAll
        static void setup(@Autowired InvoicingApi invoicingApi) throws InvalidPropertyDataException {
            var customerId = invoicingApi.createCustomer(null, "vat-" + UUID.randomUUID()).getIdentity().getEntityId();
            CUSTOMER_ID_UPDATE = customerId.toString();

            var invoiceId = invoicingApi.createInvoice("invoice-" + UUID.randomUUID(), false, false, customerId, null)
                    .getIdentity().getEntityId();
            INVOICE_ID_UPDATE = invoiceId.toString();
        }

        @ParameterizedTest
        @MethodSource({"basicUrls"})
        void doesNotParseAsJson(HttpMethod method, String url) throws Exception {
            mockMvc.perform(request(method, url)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaTypes.HAL_FORMS_JSON, MediaTypes.HAL_JSON)
                            .content("""
                                    { "my-invalid-json }
                                    """)
                    )
                    .andExpect(problemDetails()
                            .withStatusCode(HttpStatus.BAD_REQUEST)
                            .withType(PROBLEM_TYPE_PREFIX + "invalid-request/body/json")
                    );
        }

        @ParameterizedTest
        @MethodSource({"basicUrls"})
        void typeMismatchStringToNumber(HttpMethod method, String url) throws Exception {
            mockMvc.perform(request(method, url)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaTypes.HAL_FORMS_JSON, MediaTypes.HAL_JSON)
                            .content("""
                                    {
                                        "vat": "123",
                                        "total_spend": "none yet"
                                }
                                """)
                    )
                    .andExpect(validationConstraintViolation()
                            .withError(error -> error
                                    .withType("https://contentgrid.cloud/problems/input/validation/type")
                                    .withTitle("Invalid data type")
                                    .withDetail("Expected value of type long, but got string")
                                    .withField("expected_type", "long")
                                    .withField("actual_type", "string")
                                    .withField("field", "total_spend"))
                    );

        }

        @ParameterizedTest
        @MethodSource({"basicUrls"})
        void typeMismatchStringToObject(HttpMethod method, String url) throws Exception {
            mockMvc.perform(request(method, url)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaTypes.HAL_FORMS_JSON, MediaTypes.HAL_JSON)
                            .content("""
                                    {
                                        "vat": "456",
                                        "content": "XYZ"
                                    }
                                    """)
                    )
                    .andExpect(validationConstraintViolation()
                            .withError(error -> error
                                    .withType("https://contentgrid.cloud/problems/input/validation/type")
                                    .withTitle("Invalid data type")
                                    .withDetail("Expected value of type object, but got string")
                                    .withField("expected_type", "object")
                                    .withField("actual_type", "string")
                                    .withField("field", "content"))
                    );
        }

        @ParameterizedTest
        @MethodSource({"basicUrls"})
        void typeMismatchStringDoesNotParseToDate(HttpMethod method, String url) throws Exception {
            mockMvc.perform(request(method, url)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaTypes.HAL_FORMS_JSON, MediaTypes.HAL_JSON)
                            .content("""
                                    {
                                        "vat": "789",
                                        "birthday": "2022-01-01"
                                    }
                                    """)
                    )
                    .andExpect(validationConstraintViolation()
                            .withError(error -> error
                                    .withType("https://contentgrid.cloud/problems/input/validation/type/format")
                                    .withTitle("Invalid format")
                                    .withDetail(d -> assertThat(d).startsWith("Expected value of type datetime, but the format is incorrect:"))
                                    .withField("expected_type", "datetime")
                                    .withField("format_error", e -> assertThat((String)e).contains("Text '2022-01-01' could not be parsed"))
                                    .withField("field", "birthday"))
                    );
        }

        @ParameterizedTest
        @MethodSource("relationUrls")
        void toOneRelationInvalidUrl(HttpMethod method, String url) throws Exception {
            mockMvc.perform(request(method, url)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaTypes.HAL_FORMS_JSON, MediaTypes.HAL_JSON)
                            .content("""
                                    {
                                        "number": "123",
                                        "counterparty": "ZZEY"
                                    }
                                    """)
                    )
                    .andExpect(validationConstraintViolation()
                            .withError(error -> error
                                    .withType("https://contentgrid.cloud/problems/input/validation/type/format")
                                    .withTitle("Invalid format")
                                    .withDetail(d -> assertThat(d).startsWith("Expected value of type relation to entity 'customer', but the format is incorrect:"))
                                    .withField("expected_type", "entity:customer")
                                    .withField("format_error", "Invalid entity URL 'ZZEY'")
                                    .withField("field", "counterparty"))
                    );
        }

        @ParameterizedTest
        @MethodSource("relationUrls")
        void toOneRelationDifferentEntityUrl(HttpMethod method, String url) throws Exception {
            mockMvc.perform(request(method, url)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaTypes.HAL_FORMS_JSON, MediaTypes.HAL_JSON)
                            .content("""
                                    {
                                        "number": "123",
                                        "counterparty": "http://localhost/invoices/%s"
                                    }
                                    """.formatted(INVOICE_ID_UPDATE))
                    )
                    .andExpect(validationConstraintViolation()
                                    .withError(error -> error
                                            .withType("https://contentgrid.cloud/problems/input/validation/type")
                                            .withTitle("Invalid data type")
                                            .withDetail("Expected value of type relation to entity 'customer', but got relation to entity 'invoice'")
                                            .withField("expected_type", "entity:customer")
                                            .withField("actual_type", "entity:invoice")
                                            .withField("field", "counterparty"))
                    )
            ;
        }

        static Stream<Arguments> basicUrls() {
            return Stream.of(
                    Arguments.of(HttpMethod.POST, "/customers"),
                    Arguments.of(HttpMethod.PUT, "/customers/" + CUSTOMER_ID_UPDATE),
                    Arguments.of(HttpMethod.PATCH, "/customers/" + CUSTOMER_ID_UPDATE)
            );
        }

        static Stream<Arguments> relationUrls() {
            return Stream.of(
                    // Relations are only allowed when creating an entity
                    Arguments.of(HttpMethod.POST, "/invoices")
            );
        }

    }

    /**
     * Tests all validator violation cases:
     * <ul>
     * <li>Create entity without required attribute
     * <li>Create entity without required (-to-one) relation
     * <li>Create entity with attribute value not in allowed values
     * <li>Update entity to remove/null required attribute
     * <li>Update entity to remove/null required relation
     * <li>Update entity with attribute value not in allowed values
     * <li>Remove entity relation that is required on this side
     * <li>Remove entity relation that is the target of a required one-to-one relation
     * </ul>
     */
    @Nested
    class ValidatorViolations {

        @Test
        void createEntityWithoutRequiredAttribute() throws Exception {
            var customerId = createCustomer();
            // application/json
            mockMvc.perform(post("/invoices")
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaTypes.HAL_FORMS_JSON, MediaTypes.HAL_JSON)
                            .content("""
                                    {
                                        "counterparty": "http://localhost/customers/%s"
                                    }
                                    """.formatted(customerId))
                    )
                    .andExpect(validationConstraintViolation()
                            .withError(error -> error.withField("field", "number"))
                    );

            // multipart/form-data
            mockMvc.perform(multipart(HttpMethod.POST, "/invoices")
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .accept(MediaTypes.HAL_FORMS_JSON, MediaTypes.HAL_JSON)
                            .param("counterparty", "http://localhost/customers/%s".formatted(customerId))
                    )
                    .andExpect(validationConstraintViolation()
                            .withError(error -> error.withField("field", "number"))
                    );
        }

        @Test
        void createEntityWithoutRequiredRelation() throws Exception {
            // application/json
            mockMvc.perform(post("/invoices")
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaTypes.HAL_FORMS_JSON, MediaTypes.HAL_JSON)
                            .content("""
                                    {
                                        "number": "%s"
                                    }
                                    """.formatted(UUID.randomUUID()))
                    )
                    .andExpect(validationConstraintViolation()
                            .withError(error -> error.withField("field", "counterparty"))
                    );

            // multipart/form-data
            mockMvc.perform(multipart(HttpMethod.POST, "/invoices")
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .accept(MediaTypes.HAL_FORMS_JSON, MediaTypes.HAL_JSON)
                            .param("number", UUID.randomUUID().toString())
                    )
                    .andExpect(validationConstraintViolation()
                            .withError(error -> error.withField("field", "counterparty"))
                    );
        }

        @Test
        void createEntityWithAttributeValueNotInAllowedValues() throws Exception {
            // application/json
            mockMvc.perform(post("/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaTypes.HAL_FORMS_JSON, MediaTypes.HAL_JSON)
                            .content("""
                                    {
                                        "vat": "%s",
                                        "gender": "illegal"
                                    }
                                    """.formatted(UUID.randomUUID()))
                    )
                    .andExpect(validationConstraintViolation()
                            .withError(error -> error
                                    .withType("https://contentgrid.cloud/problems/input/validation/allowed-values")
                                    .withTitle("Value is not allowed")
                                    .withDetail("The value must be one of the allowed values [female, male]")
                                    .withField("field", "gender")
                                    .withField("allowed_values", List.of("female", "male"))
                            )
                    );

            // multipart/form-data
            mockMvc.perform(multipart(HttpMethod.POST, "/customers")
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .accept(MediaTypes.HAL_FORMS_JSON, MediaTypes.HAL_JSON)
                            .param("vat", UUID.randomUUID().toString())
                            .param("gender", "illegal")
                    )
                    .andExpect(validationConstraintViolation()
                            .withError(error -> error
                                    .withType("https://contentgrid.cloud/problems/input/validation/allowed-values")
                                    .withTitle("Value is not allowed")
                                    .withDetail("The value must be one of the allowed values [female, male]")
                                    .withField("field", "gender")
                                    .withField("allowed_values", List.of("female", "male"))
                            )
                    );
        }

        @Test
        void updateEntityRemoveRequiredAttribute() throws Exception {
            var invoiceId = createInvoice();
            var customerId = createCustomer();
            mockMvc.perform(put("/invoices/{id}", invoiceId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaTypes.HAL_FORMS_JSON, MediaTypes.HAL_JSON)
                            .content("""
                                    {
                                        "counterparty": "http://localhost/customers/%s"
                                    }
                                    """.formatted(customerId))
                    )
                    .andExpect(validationConstraintViolation()
                            .withError(error -> error.withField("field", "number"))
                    );

            mockMvc.perform(patch("/invoices/{id}", invoiceId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaTypes.HAL_FORMS_JSON, MediaTypes.HAL_JSON)
                            .content("""
                                    {
                                        "number": null
                                    }
                                    """)
                    )
                    .andExpect(validationConstraintViolation()
                            .withError(error -> error.withField("field", "number"))
                    );
        }

        @Test
        void updateEntityWithAttributeValueNotInAllowedValues() throws Exception {
            var customerId = createCustomer();
            mockMvc.perform(put("/customers/{id}", customerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaTypes.HAL_FORMS_JSON, MediaTypes.HAL_JSON)
                            .content("""
                                    {
                                        "vat": "new-vat",
                                        "gender": "illegal"
                                    }
                                    """)
                    )
                    .andExpect(validationConstraintViolation()
                            .withError(error -> error
                                    .withType("https://contentgrid.cloud/problems/input/validation/allowed-values")
                                    .withTitle("Value is not allowed")
                                    .withDetail("The value must be one of the allowed values [female, male]")
                                    .withField("field", "gender")
                                    .withField("allowed_values", List.of("female", "male"))
                            )
                    );

            mockMvc.perform(patch("/customers/{id}", customerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaTypes.HAL_FORMS_JSON, MediaTypes.HAL_JSON)
                            .content("""
                                    {
                                        "gender": "illegal"
                                    }
                                    """)
                    )
                    .andExpect(validationConstraintViolation()
                            .withError(error -> error
                                    .withType("https://contentgrid.cloud/problems/input/validation/allowed-values")
                                    .withTitle("Value is not allowed")
                                    .withDetail("The value must be one of the allowed values [female, male]")
                                    .withField("field", "gender")
                                    .withField("allowed_values", List.of("female", "male"))
                            )
                    );
        }

        @Test
        void removeRequiredEntityRelation_thisSide() throws Exception {
            var invoiceId = createInvoice();
            mockMvc.perform(delete("/invoices/{id}/counterparty", invoiceId))
                    .andExpect(validationConstraintViolation()
                            .withError(error -> error
                                    .withType("https://contentgrid.cloud/problems/input/validation/required")
                                    .withTitle("Mandatory field")
                                    .withDetail("A value must be present, but it is missing or empty")
                                    .withField("field", "counterparty"))
                    );
        }

        @Test
        void removeRequiredEntityRelation_otherSide() throws Exception {
            var invoiceId = createInvoice();
            var refund = invoicingApi.createRefund(invoiceId);
            // Now there is a refund that references our invoice

            mockMvc.perform(delete("/invoices/{id}/refund", invoiceId))
                    .andExpect(problemDetails()
                            .withType("https://contentgrid.cloud/problems/integrity/required-relation")
                            .withStatusCode(HttpStatus.CONFLICT)
                            .withTitle("Relation is required")
                            .withDetail("Relation 'invoice' on Entity 'refund' %s is required".formatted(refund.getIdentity().getEntityId().getValue()))
                            .withField("affected_relation", "http://localhost/refunds/%s/invoice".formatted(refund.getIdentity().getEntityId().getValue()))
                    );
        }
    }

    /**
     * Tests all deletion violation cases:
     * <ul>
     * <li>Delete entity that is the target of a required many-to-one relation
     * <li>Delete entity that is the target of a required one-to-one relation
     * </ul>
     */
    @Nested
    class DeletionViolations {

        @Test
        @Disabled("ACC-2416: problem details not wrapped in a validation constraint violation")
        void deleteEntity_targetOfRequiredManyToOneRelation() throws Exception {
            var invoiceId = createInvoice();
            var counterparty = invoicingApi.findInvoiceCounterparty(invoiceId).orElseThrow();
            // This customer is linked to the invoice
            mockMvc.perform(delete("/customers/{id}", counterparty.getIdentity().getEntityId()))
                    .andExpect(validationConstraintViolation()
                            .withStatusCode(HttpStatus.CONFLICT)
                            .withError(error -> error.withField("field", "invoices"))
                    );
        }

        @Test
        void deleteEntity_targetOfRequiredOneToOneRelation_not500() throws Exception {
            var invoiceId = createInvoice();
            invoicingApi.createRefund(invoiceId);
            // Now there is a refund that references our invoice

            mockMvc.perform(delete("/invoices/{id}", invoiceId))
                    .andExpect(problemDetails()
                            .withStatusCode(HttpStatus.CONFLICT)
                    );
        }

        @Test
        @Disabled("ACC-2416: returns 500 - PSQLException: null value in column \"invoice\" of relation \"refund\" violates not-null constraint")
        void deleteEntity_targetOfRequiredOneToOneRelation() throws Exception {
            var invoiceId = createInvoice();
            invoicingApi.createRefund(invoiceId);
            // Now there is a refund that references our invoice

            mockMvc.perform(delete("/invoices/{id}", invoiceId))
                    .andExpect(validationConstraintViolation()
                            .withStatusCode(HttpStatus.CONFLICT)
                            .withError(error -> error.withField("field", "refund"))
                    );
        }

    }

    /**
     * Tests all database constraint cases:
     * <ul>
     * <li>Unique constraint violations (unique column value created/updated for the second time)
     * <li>FK constraint violations
     * </ul>
     */
    @Nested
    class DatabaseConstraintViolations {

        @Test
        void uniqueConstraintViolation_create() throws Exception {
            var customerVat = UUID.randomUUID();

            // First time goes through
            var customerOne = mockMvc.perform(post("/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaTypes.HAL_FORMS_JSON, MediaTypes.HAL_JSON)
                            .content("""
                                    {
                                        "vat": "%s"
                                    }
                                    """.formatted(customerVat))
                    )
                    .andExpect(MockMvcResultMatchers.status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getRedirectedUrl();

            // Second time results in a unique constraint error
            mockMvc.perform(post("/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaTypes.HAL_FORMS_JSON, MediaTypes.HAL_JSON)
                            .content("""
                                    {
                                        "vat": "%s"
                                    }
                                    """.formatted(customerVat))
                    )
                    .andExpect(validationConstraintViolation()
                            .withError(error -> error
                                    .withType("https://contentgrid.cloud/problems/input/validation/duplicate")
                                    .withTitle("Value is not unique")
                                    .withDetail((String) null)
                                    .withField("field", "vat")
                                    .withField("conflicting_item", customerOne)

                            )
                    );
        }

        @Test
        void uniqueConstraintViolation_update() throws Exception {
            var customerId = createCustomer();
            var customerVat = UUID.randomUUID();

            // Create goes through
            var secondCustomer = mockMvc.perform(post("/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaTypes.HAL_FORMS_JSON, MediaTypes.HAL_JSON)
                            .content("""
                                    {
                                        "vat": "%s"
                                    }
                                    """.formatted(customerVat))
                    )
                    .andExpect(MockMvcResultMatchers.status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getRedirectedUrl();

            // Update to same id fails
            mockMvc.perform(patch("/customers/{id}", customerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaTypes.HAL_FORMS_JSON, MediaTypes.HAL_JSON)
                            .content("""
                                    {
                                        "vat": "%s"
                                    }
                                    """.formatted(customerVat))
                    )
                    .andExpect(validationConstraintViolation()
                            .withError(error -> error
                                    .withType("https://contentgrid.cloud/problems/input/validation/duplicate")
                                    .withTitle("Value is not unique")
                                    .withDetail((String) null)
                                    .withField("field", "vat")
                                    .withField("conflicting_item", secondCustomer)
                            )
                    );
        }

        @Test
        @Disabled("cases are covered by constraints, so no direct way to check it")
        void foreignKeyConstraintViolation() {

        }

    }

    /**
     * Tests all invalid collection filter values
     */
    @Nested
    class CollectionFilterValueErrors {

        @Test
        void invalidSortParameter() throws Exception {
            mockMvc.perform(get("/customers?_sort=xyz")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(problemDetails()
                            .withStatusCode(HttpStatus.BAD_REQUEST)
                            .withType(PROBLEM_TYPE_PREFIX + "invalid-query-parameter/sort/target")
                    )
//                    .andExpect(jsonPath("$.property").value("xyz"))
//                    .andExpect(jsonPath("$.query_parameter").value("_sort"))
//                    .andExpect(jsonPath("$.invalid_value").value("xyz,asc"))
            ;
        }

        @Test
        void invalidDateFilterValue() throws Exception {
            mockMvc.perform(get("/customers?birthday=invalid")
                            .accept(MediaType.APPLICATION_JSON)
                    )
                    .andExpect(problemDetails()
                            .withStatusCode(HttpStatus.BAD_REQUEST)
                            .withType(PROBLEM_TYPE_PREFIX + "invalid-query-parameter/filter/format")
                    )
                    .andExpect(jsonPath("$.query_parameter").value("birthday"))
            ;
        }

    }

    /**
     * Tests invalid pagination parameter
     */
    @Nested
    class PaginationParameterErrors {

        @Test
        void invalidSizeParameter_zero() throws Exception {
            mockMvc.perform(get("/customers?_size=0")
                            .accept(MediaType.APPLICATION_JSON)
                    )
                    .andExpect(problemDetails()
                            .withStatusCode(HttpStatus.BAD_REQUEST)
                            .withType(PROBLEM_TYPE_PREFIX + "invalid-query-parameter/pagination")
                    )
                    .andExpect(jsonPath("$.query_parameter").value("_size"));
        }

        @Test
        void invalidSizeParameter_negative() throws Exception {
            mockMvc.perform(get("/customers?_size=-10")
                            .accept(MediaType.APPLICATION_JSON)
                    )
                    .andExpect(problemDetails()
                            .withStatusCode(HttpStatus.BAD_REQUEST)
                            .withType(PROBLEM_TYPE_PREFIX + "invalid-query-parameter/pagination")
                    )
                    .andExpect(jsonPath("$.query_parameter").value("_size"));
        }

        @Test
        void invalidSizeParameter_nonnumber() throws Exception {
            mockMvc.perform(get("/customers?_size=abc")
                            .accept(MediaType.APPLICATION_JSON)
                    )
                    .andExpect(problemDetails()
                            .withStatusCode(HttpStatus.BAD_REQUEST)
                            .withType(PROBLEM_TYPE_PREFIX + "invalid-query-parameter/pagination")
                    )
                    .andExpect(jsonPath("$.query_parameter").value("_size"));
        }

        @Test
        void invalidPageParameter() throws Exception {
            mockMvc.perform(get("/customers?_cursor=abc")
                            .accept(MediaType.APPLICATION_JSON)
                    )
                    .andExpect(problemDetails()
                            .withStatusCode(HttpStatus.BAD_REQUEST)
                            .withType(PROBLEM_TYPE_PREFIX + "invalid-query-parameter/pagination")
                    )
                    .andExpect(jsonPath("$.query_parameter").value("_cursor"))
            ;
        }
    }
}
