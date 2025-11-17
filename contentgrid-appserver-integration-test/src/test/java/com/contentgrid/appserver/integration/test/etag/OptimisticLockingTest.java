package com.contentgrid.appserver.integration.test.etag;

import static com.contentgrid.appserver.integration.test.matchers.ETagHeaderMatcher.toETag;
import static com.contentgrid.appserver.integration.test.matchers.ExtendedHeaderResultMatchers.headers;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.version.ExactlyVersion;
import com.contentgrid.appserver.integration.test.fixture.invoicing.InvoicingApi;
import com.contentgrid.appserver.integration.test.fixture.invoicing.InvoicingApiApplication;
import com.contentgrid.appserver.integration.test.matchers.ETagHeaderMatcher.ETag;
import com.contentgrid.appserver.rest.test.WithMockJwt;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriTemplate;

@Slf4j
@SpringBootTest(properties = {
        "contentgrid.events.rabbitmq.enabled=false",
})
@ContextConfiguration(classes = {
        InvoicingApiApplication.class,
})
@AutoConfigureMockMvc(printOnlyOnFailure = false)
@WithMockJwt
public class OptimisticLockingTest {

    static final String INVOICE_NUMBER_1 = "I-2022-0001";
    static final String ORG_XENIT_VAT = "BE0887582365";
    static EntityId INVOICE_1_ID;
    static EntityId XENIT_ID;

    static ExactlyVersion INVOICE_1_VERSION;
    static ExactlyVersion XENIT_VERSION;
    static final ETag INVALID_VERSION = new ETag("INVALID");

    private static final String EXT_ASCII_TEXT = "L'éducation doit être gratuite.";
    private static final String UNICODE_TEXT = "Some unicode text 💩";
    private static final String MIMETYPE_PLAINTEXT_LATIN1 = "text/plain;charset=ISO-8859-1";
    private static final String MIMETYPE_PLAINTEXT_UTF8 = "text/plain;charset=UTF-8";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    InvoicingApi invoicingApi;

    @BeforeEach
    void setupTestData() throws InvalidPropertyDataException {
        var xenit = invoicingApi.createCustomer("XeniT", ORG_XENIT_VAT);

        XENIT_ID = xenit.getIdentity().getEntityId();
        XENIT_VERSION = (ExactlyVersion) xenit.getIdentity().getVersion();

        var invoice = invoicingApi.createInvoice(INVOICE_NUMBER_1, true, false, XENIT_ID, new HashSet<>());

        INVOICE_1_ID = invoice.getIdentity().getEntityId();
        INVOICE_1_VERSION = (ExactlyVersion) invoice.getIdentity().getVersion();
    }

    void setupContentProperties() throws InvalidPropertyDataException {
        var stream = new ByteArrayInputStream(EXT_ASCII_TEXT.getBytes(StandardCharsets.ISO_8859_1));

        invoicingApi.storeCustomerContent(XENIT_ID, "content.txt", MIMETYPE_PLAINTEXT_LATIN1, stream);
        var customer = invoicingApi.findCustomerByVat(ORG_XENIT_VAT).orElseThrow();

        XENIT_VERSION = (ExactlyVersion) customer.getIdentity().getVersion();

        invoicingApi.storeInvoiceContent(INVOICE_1_ID, "content.txt", MIMETYPE_PLAINTEXT_LATIN1, stream);
        var invoice = invoicingApi.findInvoiceByNumber(INVOICE_NUMBER_1).orElseThrow();

        INVOICE_1_VERSION = (ExactlyVersion) invoice.getIdentity().getVersion();
    }

    void checkETagExists(String url) throws Exception {
        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(headers().etag().exists());
    }

    void checkETagUnchanged(String url, ETag original) throws Exception {
        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(headers().etag().isEqualTo(original));
    }

    void checkETagChanged(String url, ETag original) throws Exception {
        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(headers().etag().isNotEqualTo(original));
    }

    @AfterEach
    void cleanupTestData() {
        invoicingApi.deleteAll();
    }

    @Nested
    class ItemResource {

        @Test
        void getInvoice_withInvalidIfNoneMatch_http200() throws Exception {
            mockMvc.perform(get("/invoices/" + INVOICE_1_ID)
                            .accept(MediaType.APPLICATION_JSON)
                            .header(HttpHeaders.IF_NONE_MATCH, INVALID_VERSION))
                    .andExpect(status().isOk())
                    .andExpect(headers().etag().isEqualTo(toETag(INVOICE_1_VERSION)));
        }

        @Test
        void getInvoice_withMatchingIfNoneMatch_http304() throws Exception {
            mockMvc.perform(get("/invoices/" + INVOICE_1_ID)
                            .accept(MediaType.APPLICATION_JSON)
                            .header(HttpHeaders.IF_NONE_MATCH, toETag(INVOICE_1_VERSION)))
                    .andExpect(status().isNotModified());
        }

        @Test
        void postInvoice_shouldSetETag() throws Exception {
            var response = mockMvc.perform(post("/invoices")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "number": "I-2022-0003",
                                        "counterparty": "http://localhost/customers/%s"
                                    }
                                    """.formatted(XENIT_ID)))
                    .andExpect(status().isCreated())
                    .andReturn();

            var location = Objects.requireNonNull(response.getResponse().getHeader(HttpHeaders.LOCATION));
            var matches = new UriTemplate("{scheme}://{host}/invoices/{id}").match(location);
            var invoiceId = matches.get("id");

            checkETagExists("/invoices/" + invoiceId);
        }

        @Test
        void putInvoice_withInvalidIfMatch_http412() throws Exception {
            mockMvc.perform(put("/invoices/{id}", INVOICE_1_ID)
                            .header(HttpHeaders.IF_MATCH, INVALID_VERSION)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "number": "%s",
                                        "paid": true
                                    }
                                    """.formatted(INVOICE_NUMBER_1)))
                    .andExpect(status().isPreconditionFailed());

            checkETagUnchanged("/invoices/" + INVOICE_1_ID, toETag(INVOICE_1_VERSION));
        }

        @Test
        void putInvoice_withMatchingIfMatch_http204() throws Exception {
            mockMvc.perform(put("/invoices/{id}", INVOICE_1_ID)
                            .header(HttpHeaders.IF_MATCH, toETag(INVOICE_1_VERSION))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "number": "%s",
                                        "paid": true
                                    }
                                    """.formatted(INVOICE_NUMBER_1)))
                    .andExpect(status().isNoContent());

            checkETagChanged("/invoices/" + INVOICE_1_ID, toETag(INVOICE_1_VERSION));
        }

        @Test
        void deleteInvoice_withInvalidIfMatch_http412() throws Exception {
            mockMvc.perform(delete("/invoices/{id}", INVOICE_1_ID)
                            .header(HttpHeaders.IF_MATCH, INVALID_VERSION))
                    .andExpect(status().isPreconditionFailed());

            checkETagUnchanged("/invoices/" + INVOICE_1_ID, toETag(INVOICE_1_VERSION));
        }

        @Test
        void deleteInvoice_withMatchingIfMatch_http204() throws Exception {
            mockMvc.perform(delete("/invoices/{id}", INVOICE_1_ID)
                            .header(HttpHeaders.IF_MATCH, toETag(INVOICE_1_VERSION)))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/invoices/{id}", INVOICE_1_ID))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class InlineContentProperty {

        @Test
        void postInvoiceContent_withMatchingIfMatch_http204() throws Exception {
            mockMvc.perform(post("/invoices/{id}/content", INVOICE_1_ID)
                            .header(HttpHeaders.IF_MATCH, toETag(INVOICE_1_VERSION))
                            .contentType(MediaType.TEXT_PLAIN)
                            .characterEncoding(StandardCharsets.UTF_8)
                            .content(UNICODE_TEXT))
                    .andExpect(status().isNoContent());

            checkETagChanged("/invoices/" + INVOICE_1_ID, toETag(INVOICE_1_VERSION));
        }

        @Test
        void postInvoiceContent_withInvalidIfMatch_http412() throws Exception {
            mockMvc.perform(post("/invoices/{id}/content", INVOICE_1_ID)
                            .header(HttpHeaders.IF_MATCH, INVALID_VERSION)
                            .contentType(MediaType.TEXT_PLAIN)
                            .characterEncoding(StandardCharsets.UTF_8)
                            .content(UNICODE_TEXT))
                    .andExpect(status().isPreconditionFailed());

            checkETagUnchanged("/invoices/" + INVOICE_1_ID, toETag(INVOICE_1_VERSION));
        }

        @Test
        void postMultipartInvoiceAndContent_shouldSetETag_http201() throws Exception {
            var file = new MockMultipartFile("content", "content.txt", MIMETYPE_PLAINTEXT_UTF8,
                    UNICODE_TEXT.getBytes(StandardCharsets.UTF_8));

            var response = mockMvc.perform(multipart(HttpMethod.POST, "/invoices")
                            .file(file)
                            .param("number", "I-2022-0003")
                            .param("counterparty", "http://localhost/customers/" + XENIT_ID))
                    .andExpect(status().isCreated())
                    .andReturn();

            var location = Objects.requireNonNull(response.getResponse().getHeader(HttpHeaders.LOCATION));
            var matches = new UriTemplate("{scheme}://{host}/invoices/{id}").match(location);
            var invoiceId = matches.get("id");

            checkETagExists("/invoices/" + invoiceId);
        }

        @Test
        void putInvoiceContent_withMatchingIfMatch_http204() throws Exception {
            setupContentProperties();

            // update content, ONLY changing the charset
            mockMvc.perform(put("/invoices/{id}/content", INVOICE_1_ID)
                            .header(HttpHeaders.IF_MATCH, toETag(INVOICE_1_VERSION))
                            .characterEncoding(StandardCharsets.UTF_8)
                            .contentType(MediaType.TEXT_PLAIN)
                            .content(EXT_ASCII_TEXT))
                    .andExpect(status().isNoContent());

            checkETagChanged("/invoices/" + INVOICE_1_ID, toETag(INVOICE_1_VERSION));
        }

        @Test
        void putInvoiceContent_withInvalidIfMatch_http412() throws Exception {
            setupContentProperties();

            // update content, ONLY changing the charset
            mockMvc.perform(put("/invoices/{id}/content", INVOICE_1_ID)
                            .header(HttpHeaders.IF_MATCH, INVALID_VERSION)
                            .characterEncoding(StandardCharsets.UTF_8)
                            .contentType(MediaType.TEXT_PLAIN)
                            .content(EXT_ASCII_TEXT))
                    .andExpect(status().isPreconditionFailed());

            checkETagUnchanged("/invoices/" + INVOICE_1_ID, toETag(INVOICE_1_VERSION));
        }

        @Test
        void deleteInvoiceContent_withMatchingIfMatch_http204() throws Exception {
            setupContentProperties();

            mockMvc.perform(delete("/invoices/{id}/content", INVOICE_1_ID)
                            .header(HttpHeaders.IF_MATCH, toETag(INVOICE_1_VERSION)))
                    .andExpect(status().isNoContent());

            checkETagChanged("/invoices/" + INVOICE_1_ID, toETag(INVOICE_1_VERSION));
        }

        @Test
        void deleteInvoiceContent_withInvalidIfMatch_http412() throws Exception {
            setupContentProperties();

            mockMvc.perform(delete("/invoices/{id}/content", INVOICE_1_ID)
                            .header(HttpHeaders.IF_MATCH, INVALID_VERSION))
                    .andExpect(status().isPreconditionFailed());

            checkETagUnchanged("/invoices/" + INVOICE_1_ID, toETag(INVOICE_1_VERSION));
        }
    }

    @Nested
    class EmbeddedContentProperty {

        @Test
        void postCustomerContent_withMatchingIfMatch_http204() throws Exception {
            mockMvc.perform(post("/customers/{id}/content", XENIT_ID)
                            .header(HttpHeaders.IF_MATCH, toETag(XENIT_VERSION))
                            .contentType(MediaType.TEXT_PLAIN)
                            .characterEncoding(StandardCharsets.UTF_8)
                            .content(UNICODE_TEXT))
                    .andExpect(status().isNoContent());

            checkETagChanged("/customers/" + XENIT_ID, toETag(XENIT_VERSION));
        }

        @Test
        void postCustomerContent_withInvalidIfMatch_http412() throws Exception {
            mockMvc.perform(post("/customers/{id}/content", XENIT_ID)
                            .header(HttpHeaders.IF_MATCH, INVALID_VERSION)
                            .contentType(MediaType.TEXT_PLAIN)
                            .characterEncoding(StandardCharsets.UTF_8)
                            .content(UNICODE_TEXT))
                    .andExpect(status().isPreconditionFailed());

            checkETagUnchanged("/customers/" + XENIT_ID, toETag(XENIT_VERSION));
        }

        @Test
        void postMultipartCustomerAndContent_shouldSetETag_http201() throws Exception {
            var file = new MockMultipartFile("content", "content.txt", MIMETYPE_PLAINTEXT_UTF8,
                    UNICODE_TEXT.getBytes(StandardCharsets.UTF_8));

            var response = mockMvc.perform(multipart(HttpMethod.POST, "/customers")
                            .file(file)
                            .param("name", "Example")
                            .param("vat", "BE_EXAMPLE"))
                    .andExpect(status().isCreated())
                    .andReturn();

            var location = Objects.requireNonNull(response.getResponse().getHeader(HttpHeaders.LOCATION));
            var matches = new UriTemplate("{scheme}://{host}/customers/{id}").match(location);
            var customerId = matches.get("id");

            checkETagExists("/customers/" + customerId);
        }

        @Test
        void putCustomerContent_withMatchingIfMatch_http204() throws Exception {
            setupContentProperties();

            // update content, ONLY changing the charset
            mockMvc.perform(put("/customers/{id}/content", XENIT_ID)
                            .header(HttpHeaders.IF_MATCH, toETag(XENIT_VERSION))
                            .characterEncoding(StandardCharsets.UTF_8)
                            .contentType(MediaType.TEXT_PLAIN)
                            .content(EXT_ASCII_TEXT))
                    .andExpect(status().isNoContent());

            checkETagChanged("/customers/" + XENIT_ID, toETag(XENIT_VERSION));
        }

        @Test
        void putCustomerContent_withInvalidIfMatch_http412() throws Exception {
            setupContentProperties();

            // update content, ONLY changing the charset
            mockMvc.perform(put("/customers/{id}/content", XENIT_ID)
                            .header(HttpHeaders.IF_MATCH, INVALID_VERSION)
                            .characterEncoding(StandardCharsets.UTF_8)
                            .contentType(MediaType.TEXT_PLAIN)
                            .content(EXT_ASCII_TEXT))
                    .andExpect(status().isPreconditionFailed());

            checkETagUnchanged("/customers/" + XENIT_ID, toETag(XENIT_VERSION));
        }

        @Test
        void deleteCustomerContent_withMatchingIfMatch_http204() throws Exception {
            setupContentProperties();

            mockMvc.perform(delete("/customers/{id}/content", XENIT_ID)
                            .header(HttpHeaders.IF_MATCH, toETag(XENIT_VERSION)))
                    .andExpect(status().isNoContent());

            checkETagChanged("/customers/" + XENIT_ID, toETag(XENIT_VERSION));
        }

        @Test
        void deleteCustomerContent_withInvalidIfMatch_http412() throws Exception {
            setupContentProperties();

            mockMvc.perform(delete("/customers/{id}/content", XENIT_ID)
                            .header(HttpHeaders.IF_MATCH, INVALID_VERSION))
                    .andExpect(status().isPreconditionFailed());

            checkETagUnchanged("/customers/" + XENIT_ID, toETag(XENIT_VERSION));
        }
    }
}
