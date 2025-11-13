package com.contentgrid.appserver.integration.test.fixture.invoicing;

import static com.contentgrid.appserver.integration.test.matchers.ExtendedHeaderResultMatchers.headers;
import static com.contentgrid.appserver.rest.test.ProblemDetailsMockMvcMatchers.problemDetails;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contentgrid.appserver.domain.ContentApi.Content;
import com.contentgrid.appserver.domain.data.DataEntry.BooleanDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.NullDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.StringDataEntry;
import com.contentgrid.appserver.domain.data.EntityInstance;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.rest.test.WithMockJwt;
import io.minio.MakeBucketArgs;
import io.minio.MinioAsyncClient;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Links;
import org.springframework.hateoas.mediatype.hal.CurieProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriTemplate;
import org.springframework.web.util.UriUtils;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Slf4j
@SpringBootTest(properties = {
        "contentgrid.appserver.content-store.type=s3", // Use s3 storage type for storing content
        "server.servlet.encoding.enabled=false", // disables mock-mvc enforcing charset in request
        "contentgrid.events.rabbitmq.enabled=false",
})
@AutoConfigureMockMvc(printOnlyOnFailure = false)
@WithMockJwt
@Testcontainers
class InvoicingApiApplicationTest {

    static final String INVOICE_NUMBER_1 = "I-2022-0001";
    static final String INVOICE_NUMBER_2 = "I-2022-0002";
    static final String INVOICE_NUMBER_3 = "I-2022-0003";

    static final String ORG_XENIT_VAT = "BE0887582365";
    static final String ORG_INBEV_VAT = "BE0417497106";
    static final String ORG_EXAMPLE_VAT = "BE0123456789";

    static EntityId XENIT_ID, INBEV_ID;
    static EntityId ORDER_1_ID, ORDER_2_ID;
    static EntityId INVOICE_1_ID, INVOICE_2_ID;
    static EntityId PROMO_XMAS_ID, PROMO_SHIPPING_ID, PROMO_CYBER_ID;

    static EntityId ADDRESS_ID_XENIT;

    static final String BUCKET_NAME = "test-bucket";

    static boolean BUCKET_CREATED = false;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CurieProvider curieProvider;

    @Autowired
    private InvoicingApi invoicingApi;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    MinioAsyncClient client;

    @Container
    static MinIOContainer minIOContainer = new MinIOContainer("minio/minio")
            // This makes minio accept virtual host bucket access
            .withEnv("MINIO_DOMAIN", "localhost")
            .withUserName("test")
            .withPassword("password");

    @DynamicPropertySource
    static void s3Properties(DynamicPropertyRegistry registry) {
        registry.add("contentgrid.appserver.content.s3.url", () -> minIOContainer.getS3URL());
        registry.add("contentgrid.appserver.content.s3.accessKey", () -> minIOContainer.getUserName());
        registry.add("contentgrid.appserver.content.s3.secretKey", () -> minIOContainer.getPassword());
        registry.add("contentgrid.appserver.content.s3.bucket", () -> BUCKET_NAME);
    }

    void doInTransaction(ThrowingCallable callable) {
        new TransactionTemplate(this.transactionManager).execute(status -> {
            try {
                callable.call();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    @BeforeEach
    void setupTestData() throws Exception {
        if (!BUCKET_CREATED) {
            // Create the bucket if it doesn't exist yet
            client.makeBucket(MakeBucketArgs.builder().bucket(BUCKET_NAME).build());
            BUCKET_CREATED = true;
        }
        PROMO_XMAS_ID = invoicingApi.createPromotionCampaign("XMAS-2022", "10% off ").getIdentity().getEntityId();
        PROMO_SHIPPING_ID = invoicingApi.createPromotionCampaign("FREE-SHIP", "Free Shipping").getIdentity().getEntityId();
        var promoCyber = invoicingApi.createPromotionCampaign("CYBER-MON", "Cyber Monday");
        PROMO_CYBER_ID = promoCyber.getIdentity().getEntityId();

        var xenit = invoicingApi.createCustomer("XeniT", ORG_XENIT_VAT);
        var inbev = invoicingApi.createCustomer("AB InBev", ORG_INBEV_VAT);

        XENIT_ID = xenit.getIdentity().getEntityId();
        INBEV_ID = inbev.getIdentity().getEntityId();

        var address = invoicingApi.createShippingAddress("Diestsevest 32", "3000", "Leuven");
        ADDRESS_ID_XENIT = address.getIdentity().getEntityId();

        var order1 = invoicingApi.createOrder(XENIT_ID, ADDRESS_ID_XENIT, Set.of(promoCyber.getIdentity().getEntityId()));
        var order2 = invoicingApi.createOrder(XENIT_ID);
        var order3 = invoicingApi.createOrder(INBEV_ID);

        ORDER_1_ID = order1.getIdentity().getEntityId();
        ORDER_2_ID = order2.getIdentity().getEntityId();

        INVOICE_1_ID = invoicingApi.createInvoice(INVOICE_NUMBER_1, true, false, XENIT_ID, new HashSet<>(List.of(order1.getIdentity().getEntityId(), order2.getIdentity().getEntityId())))
                .getIdentity().getEntityId();
        INVOICE_2_ID = invoicingApi.createInvoice(INVOICE_NUMBER_2, false, true, INBEV_ID, new HashSet<>(List.of(order3.getIdentity().getEntityId())))
                .getIdentity().getEntityId();
    }

    @AfterEach
    void cleanupTestData() {
        invoicingApi.deleteAll();
    }

    private Matcher<Object> curies() {
        var curies = ((List<Link>) curieProvider.getCurieInformation(Links.NONE))
                .stream()
                .map(curie -> Map.of(
                        "href", curie.getHref(),
                        "templated", curie.isTemplated(),
                        "name", curie.getName()
                ))
                .toList();
        return new BaseMatcher<Object>() {
            @Override
            public boolean matches(Object actual) {
                if (actual instanceof List<?> items) {
                    return curies.equals(items);
                }
                return false;
            }

            @Override
            public void describeTo(Description description) {
                description.appendValueList("[", ", ", "]", curies);
            }
        };
    }

    @Nested
    class CollectionResource {

        @Nested
        @DisplayName("GET /{repository}/")
        class Get {

            @Test
            void listInvoices_returns_http200_ok() throws Exception {
                mockMvc.perform(get("/invoices")
                                .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.page.size").value(20))
                        .andExpect(jsonPath("$.page.total_items_exact").value(2))
                        .andExpect(jsonPath("$._embedded.['item'].length()").value(2))
                        .andExpect(jsonPath("$._embedded.['item'][0].number").exists())
                        .andExpect(jsonPath("$._links.self.href", containsString("_cursor=")))
                        .andExpect(jsonPath("$._links.curies").value(curies()));
            }

            @Test
            void listInvoices_withFilter_returns_http200_ok() throws Exception {
                mockMvc.perform(get("/invoices?number={number}", INVOICE_NUMBER_1)
                                .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$._embedded.['item'].length()").value(1))
                        .andExpect(jsonPath("$._embedded.['item'][0].number").value(INVOICE_NUMBER_1));
            }

            @Test
            @Disabled("case-insensitive filters no longer supported")
            void listInvoices_withFilter_ignoreCase_returns_http200_ok() throws Exception {
                mockMvc.perform(get("/invoices?number={number}", INVOICE_NUMBER_1.toLowerCase(Locale.ROOT))
                                .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$._embedded.['item'].length()").value(1))
                        .andExpect(jsonPath("$._embedded.['item'][0].number").value(INVOICE_NUMBER_1));
            }

            @Test
            void listRefunds_returns_http200_ok() throws Exception {
                mockMvc.perform(get("/refunds").contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.page.size").value(20))
                        .andExpect(jsonPath("$.page.total_items_exact").value(0))
                        .andExpect(jsonPath("$._embedded.['item'].length()").value(0))
                        .andExpect(jsonPath("$._links.self.href", containsString("_cursor=")))
                        .andExpect(jsonPath("$._links.curies").value(curies()));
            }

            @Test
            void listShippingLabels_withLabelsInLoop_http200_ok() throws Exception {
                // set up the loop of shipping-labels
                var label1Id = invoicingApi.createShippingLabel("a", "b").getIdentity().getEntityId();
                var label2Id = invoicingApi.createShippingLabel("b", "a").getIdentity().getEntityId();

                // Add relations
                invoicingApi.setShippingLabelParent(label1Id, label2Id);
                invoicingApi.setShippingLabelParent(label2Id, label1Id);

                mockMvc.perform(get("/shipping-labels")
                                .accept(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk());
            }
        }

        @Nested
        @DisplayName("GET /{repository/ - sorting")
        class GetSorted {

            @Test
            void sortInvoices_number_returns_http200_ok() throws Exception {
                mockMvc.perform(get("/invoices?_sort=number")
                                .accept(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$._embedded.item[0].number").value(INVOICE_NUMBER_1))
                        .andExpect(jsonPath("$._embedded.item[1].number").value(INVOICE_NUMBER_2))
                        .andExpect(jsonPath("$._links.self.href", containsString("_sort=number,asc")));
                mockMvc.perform(get("/invoices?_sort=number,desc")
                                .accept(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$._embedded.item[0].number").value(INVOICE_NUMBER_2))
                        .andExpect(jsonPath("$._embedded.item[1].number").value(INVOICE_NUMBER_1))
                        .andExpect(jsonPath("$._links.self.href", containsString("_sort=number,desc")));
            }

            @Test
            void sortInvoices_draft_returns_http400_badRequest() throws Exception {
                mockMvc.perform(get("/invoices?_sort=draft"))
                        .andExpect(problemDetails()
                                .withStatusCode(HttpStatus.BAD_REQUEST)
                                .withType("https://contentgrid.cloud/problems/invalid-query-parameter/sort")
                        );
            }

            @Test
            void sortInvoices_counterpartyBirthday_returns_http400_badRequest() throws Exception {
                mockMvc.perform(get("/invoices?_sort=counterparty.birthday"))
                        .andExpect(problemDetails()
                                .withStatusCode(HttpStatus.BAD_REQUEST)
                                .withType("https://contentgrid.cloud/problems/invalid-query-parameter/sort")
                        );
            }

            @Test
            void sortCustomers_contentSize_returns_http200_ok() throws Exception {
                var xenit = invoicingApi.findCustomerByVat(ORG_XENIT_VAT).orElseThrow();
                var stream = new ByteArrayInputStream("short-value".getBytes(StandardCharsets.UTF_8));
                invoicingApi.storeCustomerContent(xenit.getIdentity().getEntityId(), "test.txt", "text/plain", stream);

                var inbev = invoicingApi.findCustomerByVat(ORG_INBEV_VAT).orElseThrow();
                var longStream = new ByteArrayInputStream("a-longer-value".getBytes(StandardCharsets.UTF_8));
                invoicingApi.storeCustomerContent(inbev.getIdentity().getEntityId(), "test.txt", "text/plain", longStream);

                mockMvc.perform(get("/customers?_sort=content.size"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$._embedded.item[0].vat").value(ORG_XENIT_VAT))
                        .andExpect(jsonPath("$._embedded.item[1].vat").value(ORG_INBEV_VAT))
                        .andExpect(jsonPath("$._links.self.href", containsString("_sort=content.size,asc")));

            }

        }

        @Nested
        @DisplayName("HEAD /{repository}/")
        class Head {

            @Test
            void checkInvoiceCollection_shouldReturn_http200_ok() throws Exception {
                mockMvc.perform(head("/invoices")
                                .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk());
            }
        }

        @Nested
        @DisplayName("POST /{repository}/")
        class Post {

            @Test
            void createInvoice_shouldReturn_http201_created() throws Exception {
                var customerId = invoicingApi.findCustomerByVat(ORG_XENIT_VAT).orElseThrow().getIdentity().getEntityId();
                mockMvc.perform(post("/invoices")
                                .content("""
                                        {
                                            "number": "I-2022-0003",
                                            "counterparty": "/customers/%s"
                                        }
                                        """.formatted(customerId))
                                .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isCreated())
                        .andExpect(headers().location().path("/invoices/{id}"));
            }

            @Test
            void createOrder_withPromoCodes_shouldReturn_http201_created() throws Exception {
                var customerId = invoicingApi.findCustomerByVat(ORG_XENIT_VAT).orElseThrow().getIdentity().getEntityId();

                var result = mockMvc.perform(post("/orders")
                                .content("""
                                        {
                                            "customer": "/customers/%s",
                                            "promos" : [
                                                "/promotions/%s",
                                                "/promotions/%s"
                                            ]
                                        }
                                        """.formatted(customerId, PROMO_XMAS_ID, PROMO_SHIPPING_ID))
                                .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isCreated())
                        .andExpect(headers().location().path("/orders/{id}"))
                        .andReturn();

                var orderId = Optional.ofNullable(result.getResponse().getHeader(HttpHeaders.LOCATION))
                        .map(location -> new UriTemplate("{scheme}://{host}/orders/{id}").match(location))
                        .map(matches -> matches.get("id"))
                        .map(UUID::fromString)
                        .map(EntityId::of)
                        .orElseThrow();

                assertThat(invoicingApi.findOrderPromos(orderId)).hasSize(2);
            }

            @Test
            void createOrder_withMultipartFormData_noContentProperty_http201_created() throws Exception {
                var customerId = invoicingApi.findCustomerByVat(ORG_XENIT_VAT).orElseThrow().getIdentity().getEntityId();

                var result = mockMvc.perform(multipart(HttpMethod.POST, "/orders")
                                .param("customer", "/customers/" + customerId)
                                .contentType(MediaType.MULTIPART_FORM_DATA))
                        .andExpect(status().isCreated())
                        .andExpect(headers().location().path("/orders/{id}"))
                        .andReturn();

                var orderId = Optional.ofNullable(result.getResponse().getHeader(HttpHeaders.LOCATION))
                        .map(location -> new UriTemplate("{scheme}://{host}/orders/{id}").match(location))
                        .map(matches -> matches.get("id"))
                        .map(UUID::fromString)
                        .map(EntityId::of)
                        .orElseThrow();

                assertThat(invoicingApi.findOrderCustomer(orderId)).hasValueSatisfying(customer ->
                        assertThat(customer.getIdentity().getEntityId()).isEqualTo(customerId)
                );
            }
        }
    }

    @Nested
    class ItemResource {

        @Nested
        @DisplayName("GET /{repository}/{id}")
        class Get {

            @Test
            void getInvoice_shouldReturn_http200_ok() throws Exception {
                mockMvc.perform(get("/invoices/" + invoiceId(INVOICE_NUMBER_1))
                                .accept(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.number").value(INVOICE_NUMBER_1))
                        .andExpect(jsonPath("$._links.curies").value(curies()));
            }
        }

        @Nested
        @DisplayName("HEAD /{repository}/{id}")
        class Head {

            @Test
            void headInvoice_shouldReturn_http200_ok() throws Exception {
                mockMvc.perform(head("/invoices/" + invoiceId(INVOICE_NUMBER_1))
                                .accept(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk());
            }


        }

        @Nested
        @DisplayName("PUT /{repository}/{id}")
        class Put {

            @Test
            void putInvoice_shouldReturn_http204_ok() throws Exception {
                mockMvc.perform(put("/invoices/" + invoiceId(INVOICE_NUMBER_1))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "number": "%s",
                                            "paid": true
                                        }
                                        """.formatted(INVOICE_NUMBER_1)))
                        .andExpect(status().isNoContent());
                var invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1).orElseThrow();

                assertThat(invoice.getData().get("paid")).isEqualTo(new BooleanDataEntry(true));
                assertThat(invoice.getData().get("number")).isEqualTo(new StringDataEntry(INVOICE_NUMBER_1));
            }

        }

        @Nested
        @DisplayName("PATCH /{repository}/{id}")
        class Patch {

            @Test
            void patchInvoice_shouldReturn_http204_ok() throws Exception {
                mockMvc.perform(patch("/invoices/" + invoiceId(INVOICE_NUMBER_1))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "paid": true
                                        }
                                        """))
                        .andExpect(status().isNoContent());
                var invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1).orElseThrow();

                assertThat(invoice.getData().get("paid")).isEqualTo(new BooleanDataEntry(true));
                assertThat(invoice.getData().get("number")).isEqualTo(new StringDataEntry(INVOICE_NUMBER_1));
            }

        }

        @Nested
        @DisplayName("DELETE /{repository}/{id}")
        class Delete {

            @Test
            void deleteInvoice_shouldReturn_http204_ok() throws Exception {
                mockMvc.perform(delete("/invoices/" + invoiceId(INVOICE_NUMBER_1)))
                        .andExpect(status().isNoContent());

                assertThat(invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1)).isEmpty();
            }

            @Test
            void deleteInvoice_withContent_shouldReturn_http204_ok() throws Exception {
                var id = invoiceId(INVOICE_NUMBER_1);
                var stream = new ByteArrayInputStream("some-text".getBytes(StandardCharsets.UTF_8));
                invoicingApi.storeInvoiceContent(id, "test.txt", "text/plain", stream);

                mockMvc.perform(delete("/invoices/" + id))
                        .andExpect(status().isNoContent());

                assertThat(invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1)).isEmpty();
                assertThat(invoicingApi.findInvoiceContent(id)).isEmpty();
            }

            @Test
            void deleteInvoice_nonExistingId_shouldReturn_http404() throws Exception {
                mockMvc.perform(delete("/invoices/" + UUID.randomUUID()))
                        .andExpect(status().isNotFound());
            }

        }
    }

    @Nested
    class AssociationResource {

        @Nested
        @DisplayName("GET /{repository}/{id}/{property}")
        class Get {

            @Nested
            class ManyToOne {

                @Test
                void getCustomer_forInvoice_shouldReturn_http302_redirect() throws Exception {

                    mockMvc.perform(get("/invoices/" + invoiceId(INVOICE_NUMBER_1) + "/counterparty")
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isFound())
                            .andExpect(headers().location().uri("http://localhost/customers/{id}", XENIT_ID));
                }
            }

            @Nested
            class OneToMany {

                @Test
                void getInvoices_forCustomer_shouldReturn_http302_redirect() throws Exception {

                    mockMvc.perform(get("/customers/" + customerIdByVat(ORG_XENIT_VAT) + "/invoices")
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isFound())
                            .andExpect(
                                    headers().location().uri("http://localhost/invoices?counterparty={id}", XENIT_ID));
                }

                @Test
                void getOrders_forInvoice_shouldReturn_http302_redirect() throws Exception {
                    mockMvc.perform(get("/invoices/{id}/orders", INVOICE_1_ID).accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isFound())
                            .andExpect(headers().location().uri("http://localhost/orders?invoice._id={id}", INVOICE_1_ID));
                }
            }

            @Nested
            class OneToOne {

                @Test
                void getShippingAddress_forOrder_shouldReturn_http302_redirect() throws Exception {
                    mockMvc.perform(get("/orders/{id}/shippingAddress", ORDER_1_ID)
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isFound())
                            .andExpect(headers().location()
                                    .uri("http://localhost/shipping-addresses/{id}", ADDRESS_ID_XENIT));

                }
            }

            @Nested
            class ManyToMany {

                @Test
                void getPromos_forOrder_shouldReturn_http302_redirect() throws Exception {
                    mockMvc.perform(get("/orders/{id}/promos", ORDER_1_ID)
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isFound())
                            .andExpect(headers().location().uri("http://localhost/promotions?orders={id}", ORDER_1_ID));

                }

                @Test
                void getManualPromos_forOrder_shouldReturn_http302_redirect() throws Exception {
                    mockMvc.perform(get("/orders/{id}/manual-promos", ORDER_1_ID)
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isFound())
                            .andExpect(headers().location().uri("http://localhost/promotions?ordersWithManualPromos={id}", ORDER_1_ID));
                }
            }
        }

        @Nested
        @DisplayName("PUT /{repository}/{id}/{property}")
        class Put {

            @Nested
            class ManyToOne {

                @Test
                void putJson_shouldReturn_http204() throws Exception {
                    // fictive example: fix the customer
                    var correctCustomerId = invoicingApi.findCustomerByVat(ORG_INBEV_VAT).orElseThrow()
                            .getIdentity().getEntityId();
                    mockMvc.perform(put("/invoices/" + invoiceId(INVOICE_NUMBER_1) + "/counterparty")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .accept(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {
                                                "_links": {
                                                    "customer" : {
                                                        "href": "/customers/%s"
                                                    }
                                                }
                                            }
                                            """.formatted(correctCustomerId)))
                            .andExpect(status().isNoContent());
                }

            }

            @Nested
            class OneToMany {

                @Test
                void putJson_shouldReplaceLinksAndReturn_http204_noContent() throws Exception {
                    AtomicReference<EntityId> newOrderId = new AtomicReference<>(null);
                    doInTransaction(() -> {
                        var xenit = invoicingApi.findCustomerByVat(ORG_XENIT_VAT).orElseThrow().getIdentity().getEntityId();
                        newOrderId.set(invoicingApi.createOrder(xenit).getIdentity().getEntityId());
                    });
                    var invoiceNumber = invoiceId(INVOICE_NUMBER_1);

                    // set the orders using PUT, using single-link object syntax
                    mockMvc.perform(put("/invoices/%s/orders".formatted(invoiceNumber))
                                    .accept(MediaType.APPLICATION_JSON)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {
                                                "_links": {
                                                    "orders" : {
                                                        "href": "/orders/%s"
                                                    }
                                                }
                                            }
                                            """.formatted(newOrderId)))
                            .andExpect(status().isNoContent());

                    // assert orders collection has been replaced
                    assertThat(invoicingApi.findInvoiceOrders(invoiceNumber)).singleElement().satisfies(order ->
                            assertThat(order.getIdentity().getEntityId()).isEqualTo(newOrderId.get())
                    );
                }

                @Test
                void putUriList_shouldReplaceLinksAndReturn_http204_noContent() throws Exception {
                    AtomicReference<EntityId> newOrderId = new AtomicReference<>(null);
                    doInTransaction(() -> {
                        var xenit = invoicingApi.findCustomerByVat(ORG_XENIT_VAT).orElseThrow().getIdentity().getEntityId();
                        newOrderId.set(invoicingApi.createOrder(xenit).getIdentity().getEntityId());
                    });
                    mockMvc.perform(put("/invoices/{id}/orders", INVOICE_1_ID)
                            .contentType("text/uri-list")
                            .content(
                                    """
                                    /orders/%s
                                    /orders/%s
                                    """.formatted(ORDER_1_ID, newOrderId)
                            )
                    ).andExpect(status().isNoContent());

                    assertThat(invoicingApi.findInvoiceOrders(INVOICE_1_ID))
                            .map(EntityInstance::getIdentity)
                            .map(EntityIdentity::getEntityId)
                            .containsExactlyInAnyOrder(ORDER_1_ID, newOrderId.get());
                }
            }

            @Nested
            class OneToOne {

                @Test
                void putShippingAddress_forOrder_shouldReturn_http204_noContent() throws Exception {

                    var addressId = invoicingApi.createShippingAddress().getIdentity().getEntityId();

                    mockMvc.perform(put("/orders/{id}/shippingAddress", ORDER_2_ID)
                                    .accept(MediaType.APPLICATION_JSON)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {
                                                "_links": {
                                                    "shippingAddress" : {
                                                        "href": "/shipping-addresses/%s"
                                                    }
                                                }
                                            }
                                            """.formatted(addressId)))
                            .andExpect(status().isNoContent());

                    assertThat(invoicingApi.findOrderShippingAddress(ORDER_2_ID)).isNotEmpty();

                }
            }

            @Nested
            class ManyToMany {

                @Test
                void putJson_emptyPromos_forOrder_shouldReturn_http204_noContent() throws Exception {
                    assertThat(invoicingApi.findOrderPromos(ORDER_1_ID)).hasSize(1);

                    mockMvc.perform(put("/orders/{id}/promos", ORDER_1_ID)
                                    .accept(MediaType.APPLICATION_JSON)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(""))
                            .andExpect(status().isNoContent());

                    assertThat(invoicingApi.findOrderPromos(ORDER_1_ID)).hasSize(0);
                }

                @Test
                void putJson_Promos_forOrder_shouldReturn_http204_noContent() throws Exception {
                    mockMvc.perform(put("/orders/{id}/promos", ORDER_1_ID)
                                    .accept(MediaType.APPLICATION_JSON)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {
                                                "_links": {
                                                    "promos" : [
                                                        { "href": "/promotions/%s" },
                                                        { "href": "/promotions/%s" }
                                                    ]
                                                }
                                            }
                                            """.formatted(PROMO_XMAS_ID, PROMO_SHIPPING_ID))

                            )
                            .andExpect(status().isNoContent());

                    assertThat(invoicingApi.findOrderPromos(ORDER_1_ID)).hasSize(2);
                }

                @Test
                void putUriList_Promos_forOrder_shouldReturn_http204_noContent() throws Exception {
                    mockMvc.perform(put("/orders/{id}/promos", ORDER_1_ID)
                                    .accept(MediaType.APPLICATION_JSON)
                                    .contentType("text/uri-list")
                                    .content("""
                                            /promotions/%s
                                            /promotions/%s
                                            """.formatted(PROMO_XMAS_ID, PROMO_SHIPPING_ID))
                            )
                            .andExpect(status().isNoContent());

                    assertThat(invoicingApi.findOrderPromos(ORDER_1_ID)).hasSize(2);
                }
            }
        }

        @Nested
        @DisplayName("POST /{repository}/{id}/{property}")
        class Post {

            @Nested
            class ManyToOne {

                @Test
                void postJson_shouldReturn_http405_methodNotAllowed() throws Exception {

                    var correctCustomerId = invoicingApi.findCustomerByVat(ORG_INBEV_VAT).orElseThrow().getIdentity().getEntityId();
                    mockMvc.perform(post("/invoices/" + invoiceId(INVOICE_NUMBER_1) + "/counterparty")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .accept(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {
                                                "_links": {
                                                    "customer" : {
                                                        "href": "/customers/%s"
                                                    }
                                                }
                                            }
                                            """.formatted(correctCustomerId)))
                            .andExpect(status().isMethodNotAllowed());
                }
            }

            @Nested
            class OneToMany {

                @Test
                void postJson_shouldAppend_http204_noContent() throws Exception {
                    AtomicReference<EntityId> newOrderId = new AtomicReference<>(null);
                    doInTransaction(() -> {
                        var xenit = invoicingApi.findCustomerByVat(ORG_XENIT_VAT).orElseThrow().getIdentity().getEntityId();
                        newOrderId.set(invoicingApi.createOrder(xenit).getIdentity().getEntityId());
                    });

                    var invoiceNumber = invoiceId(INVOICE_NUMBER_1);

                    // add an order to an invoice
                    mockMvc.perform(post("/invoices/%s/orders".formatted(invoiceNumber))
                                    .accept(MediaType.APPLICATION_JSON)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {
                                                "_links": {
                                                    "orders" : {
                                                        "href": "/orders/%s"
                                                    }
                                                }
                                            }
                                            """.formatted(newOrderId)))
                            .andExpect(status().isNoContent());

                    // assert orders collection has been augmented
                    assertThat(invoicingApi.findInvoiceOrders(invoiceNumber))
                            .hasSize(3)
                            .anyMatch(order -> order.getIdentity().getEntityId().equals(newOrderId.get()));
                }
            }

            @Nested
            class OneToOne {

                @Test
                void postShippingAddress_forOrder_shouldReturn_http405_methodNotAllowed() throws Exception {

                    var addressId = invoicingApi.createShippingAddress().getIdentity().getEntityId();

                    mockMvc.perform(post("/orders/{id}/shippingAddress", ORDER_2_ID)
                                    .accept(MediaType.APPLICATION_JSON)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {
                                                "_links": {
                                                    "shippingAddress" : {
                                                        "href": "/shipping-addresses/%s"
                                                    }
                                                }
                                            }
                                            """.formatted(addressId)))
                            .andExpect(status().isMethodNotAllowed());
                }
            }

            @Nested
            class ManyToMany {

                @Test
                void postJson_promos_forOrder_shouldAppendLinks_http204_noContent() throws Exception {
                    mockMvc.perform(post("/orders/{id}/promos", ORDER_1_ID)
                                    .accept(MediaType.APPLICATION_JSON)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {
                                                "_links": {
                                                    "promos" : [
                                                        { "href": "/promotions/%s" },
                                                        { "href": "/promotions/%s" }
                                                    ]
                                                }
                                            }
                                            """.formatted(PROMO_XMAS_ID, PROMO_SHIPPING_ID))

                            )
                            .andExpect(status().isNoContent());

                    assertThat(invoicingApi.findOrderPromos(ORDER_1_ID)).hasSize(3);
                }

                @Test
                void postUriList_promos_forOrder_shouldAppendLinks_http204_noContent() throws Exception {
                    mockMvc.perform(post("/orders/{id}/promos", ORDER_1_ID)
                                    .accept(MediaType.APPLICATION_JSON)
                                    .contentType("text/uri-list")
                                    .content("""
                                            /promotions/%s
                                            /promotions/%s
                                            """.formatted(PROMO_XMAS_ID, PROMO_SHIPPING_ID))

                            )
                            .andExpect(status().isNoContent());

                    assertThat(invoicingApi.findOrderPromos(ORDER_1_ID)).hasSize(3);
                }
            }
        }

        @Nested
        @DisplayName("DELETE /{repository}/{id}/{property}")
        class Delete {

            @Nested
            class ManyToOne {

                @Test
                void deleteOrderCustomer_shouldReturn_http204() throws Exception {

                    mockMvc.perform(delete("/orders/" + ORDER_1_ID + "/customer")
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isNoContent());

                    assertThat(invoicingApi.findOrderCustomer(ORDER_1_ID)).isEmpty();
                }
            }

            @Nested
            class OneToMany {

                @Test
                void deleteToManyAssoc_shouldReturn_http405_methodNotAllowed() throws Exception {

                    mockMvc.perform(delete("/invoices/" + invoiceId(INVOICE_NUMBER_1) + "/orders")
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isMethodNotAllowed());
                }
            }

            @Nested
            class OneToOne {

                @Test
                void deleteShippingAddress_fromOrder_shouldReturn_http204() throws Exception {
                    assertThat(invoicingApi.findOrderShippingAddress(ORDER_1_ID)).isNotEmpty();

                    mockMvc.perform(delete("/orders/{orderId}/shippingAddress", ORDER_1_ID)
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isNoContent());

                    assertThat(invoicingApi.findOrderShippingAddress(ORDER_1_ID)).isEmpty();
                }
            }

            @Nested
            class ManyToMany {

                @Test
                void deletePromos_fromOrder_shouldReturn_http405_methodNotAllowed() throws Exception {
                    mockMvc.perform(delete("/orders/{orderId}/promos", ORDER_1_ID)
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isMethodNotAllowed());
                }
            }
        }
    }


    @Nested
    class AssociationItemResource {

        @Nested
        @DisplayName("GET /{repository}/{entityId}/{property}/{propertyId}")
        class Get {

            @Nested
            class OneToMany {

                @Test
                void getInvoicesOrders_shouldReturn_http302() throws Exception {

                    mockMvc.perform(get("/invoices/{invoice}/orders/{order}", invoiceId(INVOICE_NUMBER_1), ORDER_1_ID)
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isFound())
                            .andExpect(headers().location().uri("http://localhost/orders/{id}", ORDER_1_ID));
                }
            }

            @Nested
            class ManyToOne {

                @Test
                void getInvoiceCustomerById_shouldReturn_http302() throws Exception {
                    var invoiceId = invoiceId(INVOICE_NUMBER_1);
                    var counterPartyId = invoicingApi.findInvoiceCounterparty(invoiceId).orElseThrow().getIdentity().getEntityId();

                    mockMvc.perform(get("/invoices/" + invoiceId + "/counterparty/" + counterPartyId)
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isFound())
                            .andExpect(header().string(HttpHeaders.LOCATION,
                                    endsWith("/customers/%s".formatted(counterPartyId))));
                }

                @Test
                void getInvoiceCustomerByWrongId_shouldReturn_http404() throws Exception {
                    var invoiceId = invoiceId(INVOICE_NUMBER_1);
                    var wrongCounterparty = customerIdByVat(ORG_INBEV_VAT);

                    mockMvc.perform(get("/invoices/" + invoiceId + "/counterparty/" + wrongCounterparty)
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isNotFound());
                }
            }

            @Nested
            class ManyToMany {

                @Test
                void getPromoById_forOrder_shouldReturn_http302_redirect() throws Exception {

                    mockMvc.perform(get("/orders/{id}/promos/{promoId}", ORDER_1_ID, PROMO_CYBER_ID)
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isFound())
                            .andExpect(headers().location().uri("http://localhost/promotions/{promoId}",
                                    PROMO_CYBER_ID));

                }

                @Test
                void getPromoById_forOrder_invalidId_shouldReturn_http404_notFound() throws Exception {
                    mockMvc.perform(get("/orders/{id}/promos/{promoId}", ORDER_1_ID, PROMO_XMAS_ID)
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isNotFound());

                }
            }
        }

        @Nested
        @DisplayName("DELETE /{repository}/{entityId}/{property}/{propertyId}")
        class Delete {

            @Nested
            class OneToMany {

                @Test
                void deleteOrderById_fromInvoice_shouldReturn_http204() throws Exception {
                    assertThat(invoicingApi.findInvoiceOrders(INVOICE_1_ID))
                            .anyMatch(order -> order.getIdentity().getEntityId().equals(ORDER_1_ID));

                    mockMvc.perform(delete("/invoices/{invoice}/orders/{order}", INVOICE_1_ID, ORDER_1_ID)
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isNoContent());

                    assertThat(invoicingApi.findInvoiceOrders(INVOICE_1_ID))
                            .noneMatch(order -> order.getIdentity().getEntityId().equals(ORDER_1_ID));

                }
            }

            @Nested
            class OneToOne {

                @Test
                void deleteShippingAddressById_fromOrder_shouldReturn_http204() throws Exception {
                    assertThat(invoicingApi.findOrderShippingAddress(ORDER_1_ID)).hasValueSatisfying(address ->
                            assertThat(address.getIdentity().getEntityId()).isEqualTo(ADDRESS_ID_XENIT)
                    );

                    mockMvc.perform(
                                    delete("/orders/{orderId}/shippingAddress/{addressId}", ORDER_1_ID, ADDRESS_ID_XENIT)
                                            .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isNoContent());

                    assertThat(invoicingApi.findOrderShippingAddress(ORDER_1_ID)).isEmpty();
                }

                @Test
                void deleteShippingAddressByWrongId_fromOrder_shouldReturn_http404() throws Exception {
                    assertThat(invoicingApi.findOrderShippingAddress(ORDER_1_ID)).hasValueSatisfying(address ->
                            assertThat(address.getIdentity().getEntityId()).isEqualTo(ADDRESS_ID_XENIT)
                    );

                    mockMvc.perform(
                                    delete("/orders/{orderId}/shippingAddress/{addressId}", ORDER_1_ID, UUID.randomUUID())
                                            .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isNotFound());

                }
            }
        }
    }


    @Nested
    class ContentPropertyResource {

        private static final String EXT_ASCII_TEXT = "L'éducation doit être gratuite.";
        private static final int EXT_ASCII_TEXT_LATIN1_LENGTH = 31;
        private static final int EXT_ASCII_TEXT_UTF8_LENGTH = 33;

        private static final String UNICODE_TEXT = "Some unicode text 💩";
        private static final int UNICODE_TEXT_UTF8_LENGTH = 18 + 4;

        private static final String MIMETYPE_PLAINTEXT_UTF8 = "text/plain;charset=UTF-8";
        private static final String MIMETYPE_PLAINTEXT_LATIN1 = "text/plain;charset=ISO-8859-1";

        @Nested
        class DirectProperty {

            @Nested
            @DisplayName("GET /{repository}/{entityId}/{contentProperty}")
            class Get {

                @Test
                void getInvoiceContent() throws Exception {
                    var filename = "💩 and 📝.txt";
                    var invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1).orElseThrow();
                    var stream = new ByteArrayInputStream(EXT_ASCII_TEXT.getBytes(StandardCharsets.UTF_8));
                    invoicingApi.storeInvoiceContent(invoice.getIdentity().getEntityId(), filename, MIMETYPE_PLAINTEXT_UTF8, stream);

                    var encodedFilename = UriUtils.encodeQuery(filename, StandardCharsets.UTF_8);
                    mockMvc.perform(get("/invoices/{id}/content", invoiceId(INVOICE_NUMBER_1))
                                    .accept(MediaType.ALL_VALUE))
                            .andExpect(status().isOk())
                            .andExpect(content().contentType(MIMETYPE_PLAINTEXT_UTF8))
                            .andExpect(content().string(EXT_ASCII_TEXT))
                    ;
                            /* This assertion is changed in SB3; and is technically incorrect
                            (it should be `Content-Disposition: attachment` or `Content-Disposition: inline` with a filename, never `form-data`)
                            .andExpect(headers().string("Content-Disposition",
                                    is("form-data; name=\"attachment\"; filename*=UTF-8''%s".formatted(encodedFilename)))) */
                    ;
                }

                @Test
                void getInvoiceContent_rangeRequest_http206() throws Exception {
                    var filename = "💩 and 📝.txt";
                    var invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1).orElseThrow();
                    var byteArray = EXT_ASCII_TEXT.getBytes(StandardCharsets.UTF_8);
                    invoicingApi.storeInvoiceContent(invoice.getIdentity().getEntityId(), filename, MIMETYPE_PLAINTEXT_UTF8,
                            new ByteArrayInputStream(byteArray));
                    var start = 5;
                    var end = 9;

                    // end is inclusive in range header, but exclusive in Arrays#copyOfRange
                    var expected = Arrays.copyOfRange(byteArray, start, end + 1);

                    mockMvc.perform(get("/invoices/{id}/content", invoiceId(INVOICE_NUMBER_1))
                                    .accept(MediaType.ALL_VALUE)
                                    .header(HttpHeaders.RANGE, "bytes=%s-%s".formatted(start, end)))
                            .andExpect(status().isPartialContent())
                            .andExpect(content().contentType(MIMETYPE_PLAINTEXT_UTF8))
                            .andExpect(content().bytes(expected))
                    ;
                }

                @Test
                void getInvoiceContent_rangeRequest_upToLastByte_http206() throws Exception {
                    var filename = "💩 and 📝.txt";
                    var invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1).orElseThrow();
                    var byteArray = EXT_ASCII_TEXT.getBytes(StandardCharsets.UTF_8);
                    invoicingApi.storeInvoiceContent(invoice.getIdentity().getEntityId(), filename, MIMETYPE_PLAINTEXT_UTF8,
                            new ByteArrayInputStream(byteArray));
                    var start = 5;
                    var end = byteArray.length;
                    var expected = Arrays.copyOfRange(byteArray, start, end);

                    mockMvc.perform(get("/invoices/{id}/content", invoiceId(INVOICE_NUMBER_1))
                                    .accept(MediaType.ALL_VALUE)
                                    .header(HttpHeaders.RANGE, "bytes=%s-".formatted(start)))
                            .andExpect(status().isPartialContent())
                            .andExpect(content().contentType(MIMETYPE_PLAINTEXT_UTF8))
                            .andExpect(content().bytes(expected))
                    ;
                }

                @Test
                void getInvoiceContent_rangeRequest_lastNBytes_http206() throws Exception {
                    var filename = "💩 and 📝.txt";
                    var invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1).orElseThrow();
                    var byteArray = EXT_ASCII_TEXT.getBytes(StandardCharsets.UTF_8);
                    invoicingApi.storeInvoiceContent(invoice.getIdentity().getEntityId(), filename, MIMETYPE_PLAINTEXT_UTF8,
                            new ByteArrayInputStream(byteArray));
                    var length = 5;
                    var end = byteArray.length;
                    var expected = Arrays.copyOfRange(byteArray, end - length, end);

                    mockMvc.perform(get("/invoices/{id}/content", invoiceId(INVOICE_NUMBER_1))
                                    .accept(MediaType.ALL_VALUE)
                                    .header(HttpHeaders.RANGE, "bytes=-%s".formatted(length)))
                            .andExpect(status().isPartialContent())
                            .andExpect(content().contentType(MIMETYPE_PLAINTEXT_UTF8))
                            .andExpect(content().bytes(expected))
                    ;
                }

                @ParameterizedTest
                @CsvSource({
                        "50,54", // start > length
                        "10,9",  // start > end
                        "-1,9",  // start < 0
                })
                void getInvoiceContent_invalidRangeRequest_http416(int start, int end) throws Exception {
                    var filename = "💩 and 📝.txt";
                    var invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1).orElseThrow();
                    var byteArray = EXT_ASCII_TEXT.getBytes(StandardCharsets.UTF_8);
                    invoicingApi.storeInvoiceContent(invoice.getIdentity().getEntityId(), filename, MIMETYPE_PLAINTEXT_UTF8,
                            new ByteArrayInputStream(byteArray));

                    mockMvc.perform(get("/invoices/{id}/content", invoiceId(INVOICE_NUMBER_1))
                                    .accept(MediaType.ALL_VALUE)
                                    .header(HttpHeaders.RANGE, "bytes=%s-%s".formatted(start, end)))
                            .andExpect(status().isRequestedRangeNotSatisfiable())
                    ;
                }

                @Test
                void getInvoiceContent_missingEntity_http404() throws Exception {
                    mockMvc.perform(get("/invoices/{id}/content", UUID.randomUUID())
                                    .accept(MediaType.ALL_VALUE))
                            .andExpect(status().isNotFound());
                }

                @Test
                void getInvoiceContent_missingContent_http404() throws Exception {
                    mockMvc.perform(get("/invoices/{id}/content", invoiceId(INVOICE_NUMBER_1))
                                    .accept(MediaType.ALL_VALUE))
                            .andExpect(status().isNotFound());
                }
            }

            @Nested
            @DisplayName("POST /{repository}/{entityId}/{contentProperty}")
            class Post {

                @Test
                void postInvoiceContent_textPlainUtf8_http201() throws Exception {
                    mockMvc.perform(post("/invoices/{id}/content", invoiceId(INVOICE_NUMBER_1))
                                    .contentType(MediaType.TEXT_PLAIN)
                                    .characterEncoding(StandardCharsets.UTF_8)
                                    .content(UNICODE_TEXT))
                            .andExpect(status().isCreated());

                    var invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1).orElseThrow();

                    assertThat(invoicingApi.findInvoiceContent(invoice.getIdentity().getEntityId()))
                            .hasValueSatisfying(content -> {
                                assertThat(readContent(content)).hasContent(UNICODE_TEXT);
                                assertThat(content.getMimeType()).isEqualTo(MIMETYPE_PLAINTEXT_UTF8);
                                assertThat(content.getLength()).isEqualTo(UNICODE_TEXT_UTF8_LENGTH);
                                assertThat(content.getFilename()).isNull();
                            });
                }

                @Test
                void postInvoiceContent_textPlainLatin1_http201() throws Exception {
                    mockMvc.perform(post("/invoices/{id}/content", invoiceId(INVOICE_NUMBER_1))
                                    .contentType(MediaType.TEXT_PLAIN)
                                    .characterEncoding(StandardCharsets.ISO_8859_1)
                                    .content(EXT_ASCII_TEXT.getBytes(StandardCharsets.ISO_8859_1)))
                            .andExpect(status().isCreated());

                    var invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1).orElseThrow();

                    assertThat(invoicingApi.findInvoiceContent(invoice.getIdentity().getEntityId()))
                            .hasValueSatisfying(content -> {
                                assertThat(readContent(content))
                                        .hasBinaryContent(EXT_ASCII_TEXT.getBytes(StandardCharsets.ISO_8859_1));
                                assertThat(content.getMimeType()).isEqualTo(MIMETYPE_PLAINTEXT_LATIN1);
                                assertThat(content.getLength()).isEqualTo(EXT_ASCII_TEXT_LATIN1_LENGTH);
                                assertThat(content.getFilename()).isNull();
                            });
                }

                @Test
                void postInvoiceContent_textPlainLatin1_noCharset_http201() throws Exception {
                    mockMvc.perform(post("/invoices/{id}/content", invoiceId(INVOICE_NUMBER_1))
                                    .contentType("text/plain")
                                    .content(EXT_ASCII_TEXT.getBytes(StandardCharsets.ISO_8859_1)))
                            .andExpect(status().isCreated());

                    var invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1).orElseThrow();

                    assertThat(invoicingApi.findInvoiceContent(invoice.getIdentity().getEntityId()))
                            .hasValueSatisfying(content -> {
                                // you have to "know" the charset encoding
                                assertThat(readContent(content))
                                        .hasBinaryContent(EXT_ASCII_TEXT.getBytes(StandardCharsets.ISO_8859_1));

                                assertThat(content.getMimeType()).isEqualTo(MediaType.TEXT_PLAIN_VALUE);
                                assertThat(content.getLength()).isEqualTo(EXT_ASCII_TEXT_LATIN1_LENGTH);
                                assertThat(content.getFilename()).isNull();
                            });
                }

                @Test
                void postInvoiceAttachment_secondaryContentProperty_http201() throws Exception {
                    mockMvc.perform(post("/invoices/{id}/attachment", invoiceId(INVOICE_NUMBER_1))
                                    .contentType(MediaType.TEXT_PLAIN)
                                    .characterEncoding(StandardCharsets.UTF_8)
                                    .content(UNICODE_TEXT))
                            .andExpect(status().isCreated());

                    var invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1).orElseThrow();

                    assertThat(invoicingApi.findInvoiceAttachment(invoice.getIdentity().getEntityId()))
                            .hasValueSatisfying(content -> {
                                assertThat(readContent(content)).hasContent(UNICODE_TEXT);
                                assertThat(content.getMimeType()).isEqualTo(MIMETYPE_PLAINTEXT_UTF8);
                                assertThat(content.getLength()).isEqualTo(UNICODE_TEXT_UTF8_LENGTH);
                                assertThat(content.getFilename()).isNull();
                            });

                    assertThat(invoicingApi.findInvoiceContent(invoice.getIdentity().getEntityId())).isEmpty();
                }

                @Test
                void postInvoiceContent_update_http200() throws Exception {
                    var invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1).orElseThrow();
                    var stream = new ByteArrayInputStream(EXT_ASCII_TEXT.getBytes(StandardCharsets.ISO_8859_1));
                    invoicingApi.storeInvoiceContent(invoice.getIdentity().getEntityId(), "content.txt", MIMETYPE_PLAINTEXT_LATIN1, stream);

                    assertThat(invoicingApi.findInvoiceContent(invoice.getIdentity().getEntityId()))
                            .hasValueSatisfying(content -> {
                                assertThat(readContent(content))
                                        .hasBinaryContent(EXT_ASCII_TEXT.getBytes(StandardCharsets.ISO_8859_1));
                                assertThat(content.getMimeType()).isEqualTo(MIMETYPE_PLAINTEXT_LATIN1);
                                assertThat(content.getLength()).isEqualTo(EXT_ASCII_TEXT_LATIN1_LENGTH);
                                assertThat(content.getFilename()).isEqualTo("content.txt");
                            });

                    // update content, ONLY changing the charset
                    mockMvc.perform(post("/invoices/" + invoiceId(INVOICE_NUMBER_1) + "/content")
                                    .characterEncoding(StandardCharsets.UTF_8)
                                    .contentType(MediaType.TEXT_PLAIN)
                                    .content(EXT_ASCII_TEXT))
                            .andExpect(status().isOk());

                    invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1).orElseThrow();

                    assertThat(invoicingApi.findInvoiceContent(invoice.getIdentity().getEntityId()))
                            .hasValueSatisfying(content -> {
                                assertThat(readContent(content))
                                        .hasContent(EXT_ASCII_TEXT);
                                assertThat(content.getMimeType()).isEqualTo(MIMETYPE_PLAINTEXT_UTF8);
                                assertThat(content.getLength()).isEqualTo(EXT_ASCII_TEXT_UTF8_LENGTH);

                                // keeps original filename
                                assertThat(content.getFilename()).isEqualTo("content.txt");
                            });
                }

                @Test
                void postInvoiceContent_missingEntity_http404() throws Exception {
                    mockMvc.perform(post("/invoices/{id}/content", UUID.randomUUID())
                                    .contentType(MediaType.TEXT_PLAIN)
                                    .characterEncoding(StandardCharsets.UTF_8)
                                    .content(UNICODE_TEXT))
                            .andExpect(status().isNotFound());
                }

                @Test
                void postInvoiceContent_missingContentType_http400() throws Exception {
                    mockMvc.perform(post("/invoices/{id}/content", invoiceId(INVOICE_NUMBER_1))
                                    .content(UNICODE_TEXT))
                            .andExpect(status().isBadRequest());
                }

                @Test
                void postMultipartContent_http201() throws Exception {
                    var invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1).orElseThrow();
                    assertThat(invoicingApi.findInvoiceContent(invoice.getIdentity().getEntityId())).isEmpty();
                    assertThat(invoicingApi.findInvoiceAttachment(invoice.getIdentity().getEntityId())).isEmpty();

                    var bytes = UNICODE_TEXT.getBytes(StandardCharsets.UTF_8);
                    var file = new MockMultipartFile("file", "content.txt", MIMETYPE_PLAINTEXT_UTF8, bytes);
                    mockMvc.perform(multipart(HttpMethod.POST, "/invoices/{id}/content", invoiceId(INVOICE_NUMBER_1))
                                    .file(file))
                            .andExpect(status().isCreated());

                    invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1).orElseThrow();

                    assertThat(invoicingApi.findInvoiceContent(invoice.getIdentity().getEntityId()))
                            .hasValueSatisfying(content -> {
                                assertThat(readContent(content)).hasContent(UNICODE_TEXT);
                                assertThat(content.getMimeType()).isEqualTo(MIMETYPE_PLAINTEXT_UTF8);
                                assertThat(content.getLength()).isEqualTo(UNICODE_TEXT_UTF8_LENGTH);
                                assertThat(content.getFilename()).isEqualTo("content.txt");
                            });
                }

                @Test
                void postMultipartContent_updateDifferentContentType_http200() throws Exception {
                    var invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1).orElseThrow();
                    var bytes = EXT_ASCII_TEXT.getBytes(StandardCharsets.ISO_8859_1);
                    invoicingApi.storeInvoiceContent(invoice.getIdentity().getEntityId(), "content.txt", MIMETYPE_PLAINTEXT_LATIN1, new ByteArrayInputStream(bytes));

                    var image = new ClassPathResource("contentgrid-logo.png");
                    var content = image.getInputStream().readAllBytes();
                    var imageLength = image.contentLength();
                    var file = new MockMultipartFile("file", "logo.png", MediaType.IMAGE_PNG_VALUE, content);
                    mockMvc.perform(multipart(HttpMethod.POST, "/invoices/{id}/content", invoiceId(INVOICE_NUMBER_1))
                                    .file(file))
                            .andExpect(status().isOk());

                    invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1).orElseThrow();

                    assertThat(invoicingApi.findInvoiceContent(invoice.getIdentity().getEntityId()))
                            .hasValueSatisfying(invoiceContent -> {
                                assertThat(readContent(invoiceContent))
                                        .hasBinaryContent(content);
                                assertThat(invoiceContent.getMimeType()).isEqualTo(MediaType.IMAGE_PNG_VALUE);
                                assertThat(invoiceContent.getLength()).isEqualTo(imageLength);
                                assertThat(invoiceContent.getFilename()).isEqualTo("logo.png");
                            });
                }

                @Test
                void postMultipartContent_noPayload_http400() throws Exception {
                    var invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1).orElseThrow();
                    assertThat(invoicingApi.findInvoiceContent(invoice.getIdentity().getEntityId())).isEmpty();

                    mockMvc.perform(multipart(HttpMethod.POST, "/invoices/{id}/content", invoiceId(INVOICE_NUMBER_1)))
                            .andExpect(status().isBadRequest());
                }

                @Test
                void postMultipartEntityAndContent_missingFile_http201() throws Exception {
                    mockMvc.perform(multipart(HttpMethod.POST, "/invoices")
                                    .param("number", INVOICE_NUMBER_3)
                                    .param("counterparty", "/customers/" + customerIdByVat(ORG_XENIT_VAT)))
                            .andExpect(status().isCreated());

                    var invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_3).orElseThrow();
                    assertThat(invoicingApi.findInvoiceContent(invoice.getIdentity().getEntityId())).isEmpty();
                }

                @Test
                void postMultipartEntityAndContent_textPlainUtf8_http201() throws Exception {
                    var file = new MockMultipartFile("content", "content.txt", MIMETYPE_PLAINTEXT_UTF8,
                            UNICODE_TEXT.getBytes(StandardCharsets.UTF_8));

                    mockMvc.perform(multipart(HttpMethod.POST, "/invoices")
                                    .file(file)
                                    .param("number", INVOICE_NUMBER_3)
                                    .param("counterparty", "/customers/" + customerIdByVat(ORG_XENIT_VAT)))
                            .andExpect(status().isCreated());

                    var invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_3).orElseThrow();

                    assertThat(invoicingApi.findInvoiceContent(invoice.getIdentity().getEntityId()))
                            .hasValueSatisfying(content -> {
                                assertThat(readContent(content)).hasContent(UNICODE_TEXT);
                                assertThat(content.getMimeType()).isEqualTo(MIMETYPE_PLAINTEXT_UTF8);
                                assertThat(content.getLength()).isEqualTo(UNICODE_TEXT_UTF8_LENGTH);
                                assertThat(content.getFilename()).isEqualTo(file.getOriginalFilename());
                            });
                }

                @Test
                void postMultipartEntityAndContent_multipleContentProperties_http201() throws Exception {
                    var contentFile = new MockMultipartFile("content", "content.txt", MIMETYPE_PLAINTEXT_UTF8,
                            UNICODE_TEXT.getBytes(StandardCharsets.UTF_8));
                    var attachmentFile = new MockMultipartFile("attachment", "attachment.txt",
                            MIMETYPE_PLAINTEXT_LATIN1, EXT_ASCII_TEXT.getBytes(StandardCharsets.ISO_8859_1));

                    mockMvc.perform(multipart(HttpMethod.POST, "/invoices")
                                    .file(contentFile)
                                    .file(attachmentFile)
                                    .param("number", INVOICE_NUMBER_3)
                                    .param("counterparty", "/customers/" + customerIdByVat(ORG_XENIT_VAT)))
                            .andExpect(status().isCreated());

                    var invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_3).orElseThrow();

                    assertThat(invoicingApi.findInvoiceContent(invoice.getIdentity().getEntityId()))
                            .hasValueSatisfying(content -> {
                                assertThat(readContent(content)).hasContent(UNICODE_TEXT);
                                assertThat(content.getMimeType()).isEqualTo(MIMETYPE_PLAINTEXT_UTF8);
                                assertThat(content.getLength()).isEqualTo(UNICODE_TEXT_UTF8_LENGTH);
                                assertThat(content.getFilename()).isEqualTo(contentFile.getOriginalFilename());
                            });
                    assertThat(invoicingApi.findInvoiceAttachment(invoice.getIdentity().getEntityId()))
                            .hasValueSatisfying(content -> {
                                assertThat(readContent(content))
                                        .hasBinaryContent(EXT_ASCII_TEXT.getBytes(StandardCharsets.ISO_8859_1));
                                assertThat(content.getMimeType()).isEqualTo(MIMETYPE_PLAINTEXT_LATIN1);
                                assertThat(content.getLength()).isEqualTo(EXT_ASCII_TEXT_LATIN1_LENGTH);
                                assertThat(content.getFilename()).isEqualTo(attachmentFile.getOriginalFilename());
                            });
                }

                @Test
                void postMultipartEntity_missingRequiredAttribute_http400() throws Exception {
                    mockMvc.perform(multipart(HttpMethod.POST, "/customers")
                                    .param("name", "Example")
                            // Missing required param "vat"
                    ).andExpect(status().isBadRequest());
                }
            }

            @Nested
            @DisplayName("PUT /{repository}/{entityId}/{contentProperty}")
            class Put {

                @Test
                void putInvoiceContent_textPlainUtf8_http201() throws Exception {
                    mockMvc.perform(put("/invoices/{id}/content", invoiceId(INVOICE_NUMBER_1))
                                    .contentType(MediaType.TEXT_PLAIN)
                                    .characterEncoding(StandardCharsets.UTF_8)
                                    .content(UNICODE_TEXT))
                            .andExpect(status().isCreated());

                    var invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1).orElseThrow();

                    assertThat(invoicingApi.findInvoiceContent(invoice.getIdentity().getEntityId()))
                            .hasValueSatisfying(content -> {
                                assertThat(readContent(content)).hasContent(UNICODE_TEXT);
                                assertThat(content.getMimeType()).isEqualTo(MIMETYPE_PLAINTEXT_UTF8);
                                assertThat(content.getLength()).isEqualTo(UNICODE_TEXT_UTF8_LENGTH);
                                assertThat(content.getFilename()).isNull();
                            });
                }

                @Test
                void putInvoiceContent_textPlainLatin1_http201() throws Exception {
                    mockMvc.perform(put("/invoices/{id}/content", invoiceId(INVOICE_NUMBER_1))
                                    .contentType(MediaType.TEXT_PLAIN)
                                    .characterEncoding(StandardCharsets.ISO_8859_1)
                                    .content(EXT_ASCII_TEXT.getBytes(StandardCharsets.ISO_8859_1)))
                            .andExpect(status().isCreated());

                    var invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1).orElseThrow();

                    assertThat(invoicingApi.findInvoiceContent(invoice.getIdentity().getEntityId()))
                            .hasValueSatisfying(content -> {
                                assertThat(readContent(content))
                                        .hasBinaryContent(EXT_ASCII_TEXT.getBytes(StandardCharsets.ISO_8859_1));
                                assertThat(content.getMimeType()).isEqualTo(MIMETYPE_PLAINTEXT_LATIN1);
                                assertThat(content.getLength()).isEqualTo(EXT_ASCII_TEXT_LATIN1_LENGTH);
                                assertThat(content.getFilename()).isNull();
                            });
                }

                @Test
                void putInvoiceContent_textPlainLatin1_noCharset_http201() throws Exception {
                    mockMvc.perform(put("/invoices/{id}/content", invoiceId(INVOICE_NUMBER_1))
                                    .contentType(MediaType.TEXT_PLAIN)
                                    .content(EXT_ASCII_TEXT.getBytes(StandardCharsets.ISO_8859_1)))
                            .andExpect(status().isCreated());

                    var invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1).orElseThrow();

                    assertThat(invoicingApi.findInvoiceContent(invoice.getIdentity().getEntityId()))
                            .hasValueSatisfying(content -> {
                                // you have to "know" the charset encoding
                                assertThat(readContent(content))
                                        .hasBinaryContent(EXT_ASCII_TEXT.getBytes(StandardCharsets.ISO_8859_1));

                                assertThat(content.getMimeType()).isEqualTo(MediaType.TEXT_PLAIN_VALUE);
                                assertThat(content.getLength()).isEqualTo(EXT_ASCII_TEXT_LATIN1_LENGTH);
                                assertThat(content.getFilename()).isNull();
                            });
                }

                @Test
                void putInvoiceAttachment_secondaryContentProperty_http201() throws Exception {
                    mockMvc.perform(put("/invoices/{id}/attachment", invoiceId(INVOICE_NUMBER_1))
                                    .contentType(MediaType.TEXT_PLAIN)
                                    .characterEncoding(StandardCharsets.UTF_8)
                                    .content(UNICODE_TEXT))
                            .andExpect(status().isCreated());

                    var invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1).orElseThrow();

                    assertThat(invoicingApi.findInvoiceAttachment(invoice.getIdentity().getEntityId()))
                            .hasValueSatisfying(content -> {
                                assertThat(readContent(content)).hasContent(UNICODE_TEXT);
                                assertThat(content.getMimeType()).isEqualTo(MIMETYPE_PLAINTEXT_UTF8);
                                assertThat(content.getLength()).isEqualTo(UNICODE_TEXT_UTF8_LENGTH);
                                assertThat(content.getFilename()).isNull();
                            });

                    assertThat(invoicingApi.findInvoiceContent(invoice.getIdentity().getEntityId())).isEmpty();
                }

                @Test
                void putInvoiceContent_update_http200() throws Exception {
                    var invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1).orElseThrow();
                    var stream = new ByteArrayInputStream(EXT_ASCII_TEXT.getBytes(StandardCharsets.ISO_8859_1));
                    invoicingApi.storeInvoiceContent(invoice.getIdentity().getEntityId(), "content.txt", MIMETYPE_PLAINTEXT_LATIN1, stream);

                    assertThat(invoicingApi.findInvoiceContent(invoice.getIdentity().getEntityId()))
                            .hasValueSatisfying(content -> {
                                assertThat(readContent(content))
                                        .hasBinaryContent(EXT_ASCII_TEXT.getBytes(StandardCharsets.ISO_8859_1));
                                assertThat(content.getMimeType()).isEqualTo(MIMETYPE_PLAINTEXT_LATIN1);
                                assertThat(content.getLength()).isEqualTo(EXT_ASCII_TEXT_LATIN1_LENGTH);
                                assertThat(content.getFilename()).isEqualTo("content.txt");
                            });

                    // update content, ONLY changing the charset
                    mockMvc.perform(put("/invoices/" + invoiceId(INVOICE_NUMBER_1) + "/content")
                                    .characterEncoding(StandardCharsets.UTF_8)
                                    .contentType(MediaType.TEXT_PLAIN)
                                    .content(EXT_ASCII_TEXT))
                            .andExpect(status().isOk());

                    invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1).orElseThrow();

                    assertThat(invoicingApi.findInvoiceContent(invoice.getIdentity().getEntityId()))
                            .hasValueSatisfying(content -> {
                                assertThat(readContent(content)).hasContent(EXT_ASCII_TEXT);
                                assertThat(content.getMimeType()).isEqualTo(MIMETYPE_PLAINTEXT_UTF8);
                                assertThat(content.getLength()).isEqualTo(EXT_ASCII_TEXT_UTF8_LENGTH);

                                // keeps original filename
                                assertThat(content.getFilename()).isEqualTo("content.txt");
                            });
                }

                @Test
                void putInvoiceContent_missingEntity_http404() throws Exception {
                    mockMvc.perform(put("/invoices/{id}/content", UUID.randomUUID())
                                    .contentType(MediaType.TEXT_PLAIN)
                                    .characterEncoding(StandardCharsets.UTF_8)
                                    .content(UNICODE_TEXT))
                            .andExpect(status().isNotFound());
                }

                @Test
                void putInvoiceContent_missingContentType_http400() throws Exception {
                    mockMvc.perform(put("/invoices/{id}/content", invoiceId(INVOICE_NUMBER_1))
                                    .content(UNICODE_TEXT))
                            .andExpect(status().isBadRequest());
                }

                @Test
                void putMultipartContent_http201() throws Exception {
                    var invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1).orElseThrow();
                    assertThat(invoicingApi.findInvoiceContent(invoice.getIdentity().getEntityId())).isEmpty();
                    assertThat(invoicingApi.findInvoiceAttachment(invoice.getIdentity().getEntityId())).isEmpty();

                    var bytes = UNICODE_TEXT.getBytes(StandardCharsets.UTF_8);
                    var file = new MockMultipartFile("file", "content.txt", MIMETYPE_PLAINTEXT_UTF8, bytes);
                    mockMvc.perform(multipart(HttpMethod.PUT, "/invoices/{id}/content", invoiceId(INVOICE_NUMBER_1))
                                    .file(file))
                            .andExpect(status().isCreated());

                    invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1).orElseThrow();

                    assertThat(invoicingApi.findInvoiceContent(invoice.getIdentity().getEntityId()))
                            .hasValueSatisfying(content -> {
                                assertThat(readContent(content)).hasContent(UNICODE_TEXT);
                                assertThat(content.getMimeType()).isEqualTo(MIMETYPE_PLAINTEXT_UTF8);
                                assertThat(content.getLength()).isEqualTo(UNICODE_TEXT_UTF8_LENGTH);
                                assertThat(content.getFilename()).isEqualTo("content.txt");
                            });
                }

                @Test
                void putMultipartContent_updateDifferentContentType_http200() throws Exception {
                    var invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1).orElseThrow();
                    var bytes = EXT_ASCII_TEXT.getBytes(StandardCharsets.ISO_8859_1);
                    invoicingApi.storeInvoiceContent(invoice.getIdentity().getEntityId(), "content.txt", MIMETYPE_PLAINTEXT_LATIN1, new ByteArrayInputStream(bytes));

                    var image = new ClassPathResource("contentgrid-logo.png");
                    var content = image.getInputStream().readAllBytes();
                    var imageLength = image.contentLength();
                    var file = new MockMultipartFile("file", "logo.png", MediaType.IMAGE_PNG_VALUE, content);
                    mockMvc.perform(multipart(HttpMethod.PUT, "/invoices/{id}/content", invoiceId(INVOICE_NUMBER_1))
                                    .file(file))
                            .andExpect(status().isOk());

                    invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1).orElseThrow();

                    assertThat(invoicingApi.findInvoiceContent(invoice.getIdentity().getEntityId()))
                            .hasValueSatisfying(invoiceContent -> {
                                assertThat(readContent(invoiceContent)).hasBinaryContent(content);
                                assertThat(invoiceContent.getMimeType()).isEqualTo(MediaType.IMAGE_PNG_VALUE);
                                assertThat(invoiceContent.getLength()).isEqualTo(imageLength);
                                assertThat(invoiceContent.getFilename()).isEqualTo("logo.png");
                            });
                }

                @Test
                void putMultipartContent_noPayload_http400() throws Exception {
                    var invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1).orElseThrow();
                    assertThat(invoicingApi.findInvoiceContent(invoice.getIdentity().getEntityId())).isEmpty();

                    mockMvc.perform(multipart(HttpMethod.PUT, "/invoices/{id}/content", invoiceId(INVOICE_NUMBER_1)))
                            .andExpect(status().isBadRequest());
                }
            }

            @Nested
            @DisplayName("DELETE /{repository}/{entityId}/{contentProperty}")
            class Delete {

                @Test
                void deleteContent_http204() throws Exception {
                    var invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1).orElseThrow();
                    var bytes = EXT_ASCII_TEXT.getBytes(StandardCharsets.ISO_8859_1);
                    invoicingApi.storeInvoiceContent(invoice.getIdentity().getEntityId(), "content.txt", MIMETYPE_PLAINTEXT_LATIN1, new ByteArrayInputStream(bytes));

                    mockMvc.perform(delete("/invoices/{id}/content", invoiceId(INVOICE_NUMBER_1)))
                            .andExpect(status().isNoContent());

                    invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1).orElseThrow();
                    assertThat(invoicingApi.findInvoiceContent(invoice.getIdentity().getEntityId())).isEmpty();
                }

                @Test
                void deleteContent_noContent_http404() throws Exception {
                    mockMvc.perform(delete("/invoices/{id}/content", invoiceId(INVOICE_NUMBER_1)))
                            .andExpect(status().isNotFound());
                }

                @Test
                void deleteContent_noEntity_http404() throws Exception {
                    mockMvc.perform(delete("/invoices/{id}/content", UUID.randomUUID()))
                            .andExpect(status().isNotFound());
                }

                @Test
                void deleteMultipleContentProperties() throws Exception {
                    var invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1).orElseThrow();
                    var contentBytes = UNICODE_TEXT.getBytes(StandardCharsets.UTF_8);
                    invoicingApi.storeInvoiceContent(invoice.getIdentity().getEntityId(), "content.txt", MIMETYPE_PLAINTEXT_UTF8,
                            new ByteArrayInputStream(contentBytes));

                    var attachmentBytes = EXT_ASCII_TEXT.getBytes(StandardCharsets.ISO_8859_1);
                    invoicingApi.storeInvoiceAttachment(invoice.getIdentity().getEntityId(), "attachment.txt", MIMETYPE_PLAINTEXT_LATIN1,
                            new ByteArrayInputStream(attachmentBytes));

                    assertThat(invoicingApi.findInvoiceContent(invoice.getIdentity().getEntityId())).isNotEmpty();
                    assertThat(invoicingApi.findInvoiceAttachment(invoice.getIdentity().getEntityId())).isNotEmpty();

                    mockMvc.perform(delete("/invoices/{id}/attachment", invoiceId(INVOICE_NUMBER_1)))
                            .andExpect(status().isNoContent());

                    invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1).orElseThrow();
                    assertThat(invoicingApi.findInvoiceContent(invoice.getIdentity().getEntityId())).isNotEmpty();
                    assertThat(invoicingApi.findInvoiceAttachment(invoice.getIdentity().getEntityId())).isEmpty();

                    mockMvc.perform(delete("/invoices/{id}/content", invoiceId(INVOICE_NUMBER_1)))
                            .andExpect(status().isNoContent());

                    invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1).orElseThrow();
                    assertThat(invoicingApi.findInvoiceContent(invoice.getIdentity().getEntityId())).isEmpty();
                    assertThat(invoicingApi.findInvoiceAttachment(invoice.getIdentity().getEntityId())).isEmpty();
                }
            }
        }

        @Nested
        class EmbeddedProperty {

            @Nested
            @DisplayName("GET /{repository}/{entityId}/{contentProperty}")
            class Get {

                @Test
                void getCustomerContent() throws Exception {
                    var filename = "💩 and 📝.txt";
                    var customer = invoicingApi.findCustomerByVat(ORG_XENIT_VAT).orElseThrow();
                    var stream = new ByteArrayInputStream(EXT_ASCII_TEXT.getBytes(StandardCharsets.UTF_8));
                    invoicingApi.storeCustomerContent(customer.getIdentity().getEntityId(), filename, MIMETYPE_PLAINTEXT_UTF8, stream);

                    var encodedFilename = UriUtils.encodeQuery(filename, StandardCharsets.UTF_8);
                    mockMvc.perform(get("/customers/{id}/content", customerIdByVat(ORG_XENIT_VAT))
                                    .accept(MediaType.ALL_VALUE))
                            .andExpect(status().isOk())
                            .andExpect(content().contentType(MIMETYPE_PLAINTEXT_UTF8))
                            .andExpect(content().string(EXT_ASCII_TEXT))
                    ;
                            /* This assertion is changed in SB3; and is technically incorrect
                            (it should be `Content-Disposition: attachment` or `Content-Disposition: inline` with a filename, never `form-data`)
                            .andExpect(headers().string("Content-Disposition",
                                    is("form-data; name=\"attachment\"; filename*=UTF-8''%s".formatted(encodedFilename)))) */
                    ;
                }

                @Test
                void getInvoiceContent_rangeRequest_http206() throws Exception {
                    var filename = "💩 and 📝.txt";
                    var customer = invoicingApi.findCustomerByVat(ORG_XENIT_VAT).orElseThrow();
                    var byteArray = EXT_ASCII_TEXT.getBytes(StandardCharsets.UTF_8);
                    invoicingApi.storeCustomerContent(customer.getIdentity().getEntityId(), filename, MIMETYPE_PLAINTEXT_UTF8,
                            new ByteArrayInputStream(byteArray));
                    var start = 5;
                    var end = 9;

                    var expected = Arrays.copyOfRange(byteArray, start, end + 1);

                    mockMvc.perform(get("/customers/{id}/content", customerIdByVat(ORG_XENIT_VAT))
                                    .accept(MediaType.ALL_VALUE)
                                    .header(HttpHeaders.RANGE, "bytes=%s-%s".formatted(start, end)))
                            .andExpect(status().isPartialContent())
                            .andExpect(content().contentType(MIMETYPE_PLAINTEXT_UTF8))
                            .andExpect(content().bytes(expected))
                    ;
                }

                @Test
                void getInvoiceContent_rangeRequest_upToLastByte_http206() throws Exception {
                    var filename = "💩 and 📝.txt";
                    var customer = invoicingApi.findCustomerByVat(ORG_XENIT_VAT).orElseThrow();
                    var byteArray = EXT_ASCII_TEXT.getBytes(StandardCharsets.UTF_8);
                    invoicingApi.storeCustomerContent(customer.getIdentity().getEntityId(), filename, MIMETYPE_PLAINTEXT_UTF8,
                            new ByteArrayInputStream(byteArray));
                    var start = 5;
                    var end = byteArray.length;
                    var expected = Arrays.copyOfRange(byteArray, start, end);

                    mockMvc.perform(get("/customers/{id}/content", customerIdByVat(ORG_XENIT_VAT))
                                    .accept(MediaType.ALL_VALUE)
                                    .header(HttpHeaders.RANGE, "bytes=%s-".formatted(start)))
                            .andExpect(status().isPartialContent())
                            .andExpect(content().contentType(MIMETYPE_PLAINTEXT_UTF8))
                            .andExpect(content().bytes(expected))
                    ;
                }

                @Test
                void getInvoiceContent_rangeRequest_lastNBytes_http206() throws Exception {
                    var filename = "💩 and 📝.txt";
                    var customer = invoicingApi.findCustomerByVat(ORG_XENIT_VAT).orElseThrow();
                    var byteArray = EXT_ASCII_TEXT.getBytes(StandardCharsets.UTF_8);
                    invoicingApi.storeCustomerContent(customer.getIdentity().getEntityId(), filename, MIMETYPE_PLAINTEXT_UTF8,
                            new ByteArrayInputStream(byteArray));
                    var length = 5;
                    var end = byteArray.length;
                    var expected = Arrays.copyOfRange(byteArray, end - length, end);

                    mockMvc.perform(get("/customers/{id}/content", customerIdByVat(ORG_XENIT_VAT))
                                    .accept(MediaType.ALL_VALUE)
                                    .header(HttpHeaders.RANGE, "bytes=-%s".formatted(length)))
                            .andExpect(status().isPartialContent())
                            .andExpect(content().contentType(MIMETYPE_PLAINTEXT_UTF8))
                            .andExpect(content().bytes(expected))
                    ;
                }

                @ParameterizedTest
                @CsvSource({
                        "50,54", // start > length
                        "10,9",  // start > end
                        "-1,9",  // start < 0
                })
                void getInvoiceContent_invalidRangeRequest_http416(int start, int end) throws Exception {
                    var filename = "💩 and 📝.txt";
                    var customer = invoicingApi.findCustomerByVat(ORG_XENIT_VAT).orElseThrow();
                    var byteArray = EXT_ASCII_TEXT.getBytes(StandardCharsets.UTF_8);
                    invoicingApi.storeCustomerContent(customer.getIdentity().getEntityId(), filename, MIMETYPE_PLAINTEXT_UTF8,
                            new ByteArrayInputStream(byteArray));

                    mockMvc.perform(get("/customers/{id}/content", customerIdByVat(ORG_XENIT_VAT))
                                    .accept(MediaType.ALL_VALUE)
                                    .header(HttpHeaders.RANGE, "bytes=%s-%s".formatted(start, end)))
                            .andExpect(status().isRequestedRangeNotSatisfiable())
                    ;
                }

                @Test
                void getCustomerContent_missingEntity_http404() throws Exception {
                    mockMvc.perform(get("/customers/{id}/content", UUID.randomUUID())
                                    .accept(MediaType.ALL_VALUE))
                            .andExpect(status().isNotFound());
                }

                @Test
                void getCustomerContent_missingContent_http404() throws Exception {
                    mockMvc.perform(get("/customers/{id}/content", customerIdByVat(ORG_XENIT_VAT))
                                    .accept(MediaType.ALL_VALUE))
                            .andExpect(status().isNotFound());
                }
            }

            @Nested
            @DisplayName("POST /{repository}/{entityId}/{contentProperty}")
            class Post {

                @Test
                void postCustomerContent_textPlainUtf8_http201() throws Exception {
                    mockMvc.perform(post("/customers/{id}/content", customerIdByVat(ORG_XENIT_VAT))
                                    .contentType(MediaType.TEXT_PLAIN)
                                    .characterEncoding(StandardCharsets.UTF_8)
                                    .content(UNICODE_TEXT))
                            .andExpect(status().isCreated());

                    var customer = invoicingApi.findCustomerByVat(ORG_XENIT_VAT).orElseThrow();

                    assertThat(invoicingApi.findCustomerContent(customer.getIdentity().getEntityId()))
                            .hasValueSatisfying(content -> {
                                assertThat(readContent(content)).hasContent(UNICODE_TEXT);
                                assertThat(content.getMimeType()).isEqualTo(MIMETYPE_PLAINTEXT_UTF8);
                                assertThat(content.getLength()).isEqualTo(UNICODE_TEXT_UTF8_LENGTH);
                                assertThat(content.getFilename()).isNull();
                            });
                }

                @Test
                void postCustomerContent_update_http200() throws Exception {
                    var customer = invoicingApi.findCustomerByVat(ORG_XENIT_VAT).orElseThrow();
                    var stream = new ByteArrayInputStream(EXT_ASCII_TEXT.getBytes(StandardCharsets.ISO_8859_1));
                    invoicingApi.storeCustomerContent(customer.getIdentity().getEntityId(), "content.txt", MIMETYPE_PLAINTEXT_LATIN1, stream);

                    assertThat(invoicingApi.findCustomerContent(customer.getIdentity().getEntityId()))
                            .hasValueSatisfying(content -> {
                                assertThat(readContent(content))
                                        .hasBinaryContent(EXT_ASCII_TEXT.getBytes(StandardCharsets.ISO_8859_1));
                                assertThat(content.getMimeType()).isEqualTo(MIMETYPE_PLAINTEXT_LATIN1);
                                assertThat(content.getLength()).isEqualTo(EXT_ASCII_TEXT_LATIN1_LENGTH);
                                assertThat(content.getFilename()).isEqualTo("content.txt");
                            });

                    // update content, ONLY changing the charset
                    mockMvc.perform(post("/customers/{id}/content", customerIdByVat(ORG_XENIT_VAT))
                                    .characterEncoding(StandardCharsets.UTF_8)
                                    .contentType(MediaType.TEXT_PLAIN)
                                    .content(EXT_ASCII_TEXT))
                            .andExpect(status().isOk());

                    customer = invoicingApi.findCustomerByVat(ORG_XENIT_VAT).orElseThrow();
                    assertThat(invoicingApi.findCustomerContent(customer.getIdentity().getEntityId()))
                            .hasValueSatisfying(content -> {
                                assertThat(readContent(content))
                                        .hasContent(EXT_ASCII_TEXT);
                                assertThat(content.getMimeType()).isEqualTo(MIMETYPE_PLAINTEXT_UTF8);
                                assertThat(content.getLength()).isEqualTo(EXT_ASCII_TEXT_UTF8_LENGTH);

                                // keeps original filename
                                assertThat(content.getFilename()).isEqualTo("content.txt");
                            });
                }

                @Test
                void postCustomerContent_missingEntity_http404() throws Exception {
                    mockMvc.perform(post("/customers/{id}/content", UUID.randomUUID())
                                    .contentType(MediaType.TEXT_PLAIN)
                                    .characterEncoding(StandardCharsets.UTF_8)
                                    .content(UNICODE_TEXT))
                            .andExpect(status().isNotFound());
                }

                @Test
                void postCustomerContent_missingContentType_http400() throws Exception {
                    mockMvc.perform(post("/customers/{id}/content", customerIdByVat(ORG_XENIT_VAT))
                                    .content(UNICODE_TEXT))
                            .andExpect(status().isBadRequest());
                }

                @Test
                void postMultipartContent_http201() throws Exception {
                    var bytes = UNICODE_TEXT.getBytes(StandardCharsets.UTF_8);
                    var file = new MockMultipartFile("file", "content.txt", MIMETYPE_PLAINTEXT_UTF8, bytes);
                    mockMvc.perform(multipart("/customers/{id}/content", customerIdByVat(ORG_XENIT_VAT))
                                    .file(file))
                            .andExpect(status().isCreated());

                    var customer = invoicingApi.findCustomerByVat(ORG_XENIT_VAT).orElseThrow();

                    assertThat(invoicingApi.findCustomerContent(customer.getIdentity().getEntityId()))
                            .hasValueSatisfying(content -> {
                                assertThat(readContent(content)).hasContent(UNICODE_TEXT);
                                assertThat(content.getMimeType()).isEqualTo(MIMETYPE_PLAINTEXT_UTF8);
                                assertThat(content.getLength()).isEqualTo(UNICODE_TEXT_UTF8_LENGTH);
                                assertThat(content.getFilename()).isEqualTo(file.getOriginalFilename());
                            });
                }

                @Test
                void postMultipartEntityAndContent_missingFile_http201() throws Exception {
                    mockMvc.perform(multipart(HttpMethod.POST, "/customers")
                                    .param("name", "Example")
                                    .param("vat", ORG_EXAMPLE_VAT))
                            .andExpect(status().isCreated());

                    var customer = invoicingApi.findCustomerByVat(ORG_EXAMPLE_VAT).orElseThrow();
                    assertThat(customer.getData().get("name")).isEqualTo(new StringDataEntry("Example"));
                    assertThat(customer.getData().get("content")).isEqualTo(NullDataEntry.INSTANCE);
                }

                @Test
                void postMultipartEntityAndContent_textPlainUtf8_http201() throws Exception {
                    var file = new MockMultipartFile("content", "content.txt", MIMETYPE_PLAINTEXT_UTF8,
                            UNICODE_TEXT.getBytes(StandardCharsets.UTF_8));

                    mockMvc.perform(multipart(HttpMethod.POST, "/customers")
                                    .file(file)
                                    .param("name", "Example")
                                    .param("vat", ORG_EXAMPLE_VAT))
                            .andExpect(status().isCreated());

                    // Check whether customer exists
                    var customer = invoicingApi.findCustomerByVat(ORG_EXAMPLE_VAT).orElseThrow();

                    assertThat(invoicingApi.findCustomerContent(customer.getIdentity().getEntityId()))
                            .hasValueSatisfying(content -> {
                                assertThat(readContent(content)).hasContent(UNICODE_TEXT);
                                assertThat(content.getMimeType()).isEqualTo(MIMETYPE_PLAINTEXT_UTF8);
                                assertThat(content.getLength()).isEqualTo(UNICODE_TEXT_UTF8_LENGTH);
                                assertThat(content.getFilename()).isEqualTo(file.getOriginalFilename());
                            });
                }

                @Test
                void postMultipartEntityAndContent_camelCaseFieldName_http201() throws Exception {
                    var file = new MockMultipartFile("barcodePicture", "barcode.jpg", MIMETYPE_PLAINTEXT_UTF8,
                            UNICODE_TEXT.getBytes(StandardCharsets.UTF_8));

                    var result = mockMvc.perform(multipart(HttpMethod.POST, "/shipping-labels")
                                    .file(file)
                                    .param("from", "here")
                                    .param("to", "there"))
                            .andExpect(status().isCreated())
                            .andReturn();

                    var shippingLabelId = Optional.ofNullable(result.getResponse().getHeader(HttpHeaders.LOCATION))
                            .map(location -> new UriTemplate("{scheme}://{host}/shipping-labels/{id}").match(location))
                            .map(matches -> matches.get("id"))
                            .map(UUID::fromString)
                            .map(EntityId::of)
                            .orElseThrow();

                    assertThat(invoicingApi.findShippingLabelBarcodePicture(shippingLabelId))
                            .hasValueSatisfying(barcodePicture -> {
                                assertThat(readContent(barcodePicture)).hasContent(UNICODE_TEXT);
                                assertThat(barcodePicture.getMimeType()).isEqualTo(MIMETYPE_PLAINTEXT_UTF8);
                                assertThat(barcodePicture.getLength()).isEqualTo(UNICODE_TEXT_UTF8_LENGTH);
                                assertThat(barcodePicture.getFilename()).isEqualTo(file.getOriginalFilename());
                            });
                }

                @Test
                void postMultipartEntityAndContent_reservedFieldName_http201() throws Exception {
                    var file = new MockMultipartFile("_package", "package.bin", MIMETYPE_PLAINTEXT_UTF8,
                            UNICODE_TEXT.getBytes(StandardCharsets.UTF_8));

                    var result = mockMvc.perform(multipart(HttpMethod.POST, "/shipping-labels")
                                    .file(file)
                                    .param("from", "here")
                                    .param("to", "there"))
                            .andExpect(status().isCreated())
                            .andReturn();

                    var shippingLabelId = Optional.ofNullable(result.getResponse().getHeader(HttpHeaders.LOCATION))
                            .map(location -> new UriTemplate("{scheme}://{host}/shipping-labels/{id}").match(location))
                            .map(matches -> matches.get("id"))
                            .map(UUID::fromString)
                            .map(EntityId::of)
                            .orElseThrow();

                    assertThat(invoicingApi.findShippingLabelPackage(shippingLabelId))
                            .hasValueSatisfying(pkg -> {
                                assertThat(readContent(pkg)).hasContent(UNICODE_TEXT);
                                assertThat(pkg.getMimeType()).isEqualTo(MIMETYPE_PLAINTEXT_UTF8);
                                assertThat(pkg.getLength()).isEqualTo(UNICODE_TEXT_UTF8_LENGTH);
                                assertThat(pkg.getFilename()).isEqualTo(file.getOriginalFilename());
                            });
                }

            }

            @Nested
            @DisplayName("PUT /{repository}/{entityId}/{contentProperty}")
            class Put {

                @Test
                void putCustomerContent_textPlainUtf8_http201() throws Exception {
                    mockMvc.perform(put("/customers/{id}/content", customerIdByVat(ORG_XENIT_VAT))
                                    .contentType(MediaType.TEXT_PLAIN)
                                    .characterEncoding(StandardCharsets.UTF_8)
                                    .content(UNICODE_TEXT))
                            .andExpect(status().isCreated());

                    var customer = invoicingApi.findCustomerByVat(ORG_XENIT_VAT).orElseThrow();

                    assertThat(invoicingApi.findCustomerContent(customer.getIdentity().getEntityId()))
                            .hasValueSatisfying(content -> {
                                assertThat(readContent(content)).hasContent(UNICODE_TEXT);
                                assertThat(content.getMimeType()).isEqualTo(MIMETYPE_PLAINTEXT_UTF8);
                                assertThat(content.getLength()).isEqualTo(UNICODE_TEXT_UTF8_LENGTH);
                                assertThat(content.getFilename()).isNull();
                            });
                }

                @Test
                void putMultipartContent_http201() throws Exception {
                    var bytes = UNICODE_TEXT.getBytes(StandardCharsets.UTF_8);
                    var file = new MockMultipartFile("file", "content.txt", MIMETYPE_PLAINTEXT_UTF8, bytes);
                    mockMvc.perform(multipart(HttpMethod.PUT, "/customers/{id}/content", customerIdByVat(ORG_XENIT_VAT))
                                    .file(file))
                            .andExpect(status().isCreated());

                    var customer = invoicingApi.findCustomerByVat(ORG_XENIT_VAT).orElseThrow();

                    assertThat(invoicingApi.findCustomerContent(customer.getIdentity().getEntityId()))
                            .hasValueSatisfying(content -> {
                                assertThat(readContent(content)).hasContent(UNICODE_TEXT);
                                assertThat(content.getMimeType()).isEqualTo(MIMETYPE_PLAINTEXT_UTF8);
                                assertThat(content.getLength()).isEqualTo(UNICODE_TEXT_UTF8_LENGTH);
                                assertThat(content.getFilename()).isEqualTo(file.getOriginalFilename());
                            });
                }

            }

            @Nested
            @DisplayName("DELETE /{repository}/{entityId}/{contentProperty}")
            class Delete {

                @Test
                void deleteContent_http204() throws Exception {
                    var customer = invoicingApi.findCustomerByVat(ORG_XENIT_VAT).orElseThrow();
                    var bytes = EXT_ASCII_TEXT.getBytes(StandardCharsets.ISO_8859_1);
                    invoicingApi.storeCustomerContent(customer.getIdentity().getEntityId(), "content.txt", MIMETYPE_PLAINTEXT_LATIN1,
                            new ByteArrayInputStream(bytes));

                    mockMvc.perform(delete("/customers/{id}/content", customerIdByVat(ORG_XENIT_VAT)))
                            .andExpect(status().isNoContent());

                    customer = invoicingApi.findCustomerByVat(ORG_XENIT_VAT).orElseThrow();
                    assertThat(invoicingApi.findCustomerContent(customer.getIdentity().getEntityId())).isEmpty();
                }

                @Test
                void deleteContent_noContent_http404() throws Exception {
                    mockMvc.perform(delete("/customers/{id}/content", customerIdByVat(ORG_XENIT_VAT)))
                            .andExpect(status().isNotFound());
                }

                @Test
                void deleteContent_noEntity_http404() throws Exception {
                    mockMvc.perform(delete("/customers/{id}/content", UUID.randomUUID()))
                            .andExpect(status().isNotFound());
                }

            }
        }
    }

    private EntityId invoiceId(String number) {
        return invoicingApi.findInvoiceByNumber(number)
                .map(EntityInstance::getIdentity)
                .map(EntityIdentity::getEntityId)
                .orElseThrow();
    }

    private EntityId customerIdByVat(String vat) {
        return invoicingApi.findCustomerByVat(vat)
                .map(EntityInstance::getIdentity)
                .map(EntityIdentity::getEntityId)
                .orElseThrow();
    }

    private InputStream readContent(Content content) {
        // Retrieve the InputStream, to be used inside a lambda function
        try {
            return content.getInputStream();
        } catch (IOException e) {
            Assertions.fail(e);
            throw new RuntimeException(e);
        }
    }


}
