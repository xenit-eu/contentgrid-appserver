package com.contentgrid.appserver.integration.test.auditing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.integration.test.fixture.invoicing.InvoicingApi;
import com.contentgrid.appserver.integration.test.fixture.invoicing.InvoicingApiApplication;
import com.contentgrid.appserver.rest.test.WithMockJwt;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "contentgrid.events.rabbitmq.enabled=false",
})
@ContextConfiguration(classes = {
        InvoicingApiApplication.class,
})
@AutoConfigureMockMvc(printOnlyOnFailure = false)
@WithMockJwt
@Disabled("ACC-2419 Last-Modified, If-Modified-Since and If-Unmodified-since not supported")
class ContentLastModifiedTest {

    static final String INVOICE_NUMBER_1 = "I-2022-0001";
    static final String ORG_XENIT_VAT = "BE0887582365";

    static EntityId XENIT_ID;
    static EntityId INVOICE_1_ID;

    static String CUSTOMER_CONTENT_URL;
    static String INVOICE_CONTENT_URL;

    static Instant CUSTOMER_TIMESTAMP;
    static Instant INVOICE_TIMESTAMP;

    private static final String EXT_ASCII_TEXT = "L'éducation doit être gratuite.";
    private static final String MIMETYPE_PLAINTEXT_LATIN1 = "text/plain;charset=ISO-8859-1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    InvoicingApi invoicingApi;

    String formatInstant(Instant date) {
        DateTimeFormatter formatter = DateTimeFormatter.RFC_1123_DATE_TIME
                .withZone(ZoneId.of("GMT"));
        return formatter.format(date);
    }

    @BeforeEach
    void setupTestData() throws Exception {
        var xenit = invoicingApi.createCustomer("XeniT", ORG_XENIT_VAT);

        XENIT_ID = xenit.getIdentity().getEntityId();
        CUSTOMER_CONTENT_URL = "/customers/%s/content".formatted(XENIT_ID);

        INVOICE_1_ID = invoicingApi.createInvoice(INVOICE_NUMBER_1, true, false, XENIT_ID, new HashSet<>())
                .getIdentity().getEntityId();
        INVOICE_CONTENT_URL = "/invoices/%s/content".formatted(INVOICE_1_ID);

        var stream = new ByteArrayInputStream(EXT_ASCII_TEXT.getBytes(StandardCharsets.ISO_8859_1));
        invoicingApi.storeCustomerContent(XENIT_ID, "content.txt", MIMETYPE_PLAINTEXT_LATIN1, stream);

        stream = new ByteArrayInputStream(EXT_ASCII_TEXT.getBytes(StandardCharsets.ISO_8859_1));
        invoicingApi.storeInvoiceContent(INVOICE_1_ID, "content.txt", MIMETYPE_PLAINTEXT_LATIN1, stream);

        INVOICE_TIMESTAMP = getLastModified(INVOICE_CONTENT_URL);
        CUSTOMER_TIMESTAMP = getLastModified(CUSTOMER_CONTENT_URL);
    }

    @AfterEach
    void cleanupTestData() {
        invoicingApi.deleteAll();
    }

    private Instant getLastModified(String url) throws Exception {
        return Instant.ofEpochMilli(mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getDateHeader(HttpHeaders.LAST_MODIFIED));
    }

    @Nested
    class InlinedContentProperty {

        @Test
        void getInvoiceContent_withRecentIfModifiedSince_http304() throws Exception {
            var headerDate = INVOICE_TIMESTAMP.plus(1, ChronoUnit.MINUTES);

            mockMvc.perform(get(INVOICE_CONTENT_URL)
                            .accept(MediaType.TEXT_PLAIN)
                            .header(HttpHeaders.IF_MODIFIED_SINCE, formatInstant(headerDate)))
                    .andExpect(status().isNotModified());
        }

        @Test
        void getInvoiceContent_withOutdatedIfModifiedSince_http200() throws Exception {
            var headerDate = INVOICE_TIMESTAMP.minus(1, ChronoUnit.MINUTES);

            mockMvc.perform(get(INVOICE_CONTENT_URL)
                            .accept(MediaType.TEXT_PLAIN)
                            .header(HttpHeaders.IF_MODIFIED_SINCE, formatInstant(headerDate)))
                    .andExpect(status().isOk());
        }

        @Test
        void putInvoiceContent_withRecentIfUnmodifiedSince_http204() throws Exception {
            var headerDate = INVOICE_TIMESTAMP.plus(1, ChronoUnit.MINUTES);

            // update content, ONLY changing the charset
            mockMvc.perform(put(INVOICE_CONTENT_URL)
                            .characterEncoding(StandardCharsets.UTF_8)
                            .contentType(MediaType.TEXT_PLAIN)
                            .content(EXT_ASCII_TEXT)
                            .header(HttpHeaders.IF_UNMODIFIED_SINCE, formatInstant(headerDate)))
                    .andExpect(status().isNoContent());

            assertThat(getLastModified(INVOICE_CONTENT_URL)).isAfterOrEqualTo(INVOICE_TIMESTAMP);
        }

        @Test
        void putInvoiceContent_withOutdatedIfUnmodifiedSince_http412() throws Exception {
            var headerDate = INVOICE_TIMESTAMP.minus(1, ChronoUnit.MINUTES);

            // update content, ONLY changing the charset
            mockMvc.perform(put(INVOICE_CONTENT_URL)
                            .characterEncoding(StandardCharsets.UTF_8)
                            .contentType(MediaType.TEXT_PLAIN)
                            .content(EXT_ASCII_TEXT)
                            .header(HttpHeaders.IF_UNMODIFIED_SINCE, formatInstant(headerDate)))
                    .andExpect(status().isPreconditionFailed());

            assertThat(getLastModified(INVOICE_CONTENT_URL)).isEqualTo(INVOICE_TIMESTAMP);
        }

        @Test
        void deleteInvoiceContent_withRecentIfUnmodifiedSince_http204() throws Exception {
            var headerDate = INVOICE_TIMESTAMP.plus(1, ChronoUnit.MINUTES);

            mockMvc.perform(delete(INVOICE_CONTENT_URL)
                            .header(HttpHeaders.IF_UNMODIFIED_SINCE, formatInstant(headerDate)))
                    .andExpect(status().isNoContent());
        }

        @Test
        void deleteInvoiceContent_withOutdatedIfUnmodifiedSince_http412() throws Exception {
            var headerDate = INVOICE_TIMESTAMP.minus(1, ChronoUnit.MINUTES);

            mockMvc.perform(delete(INVOICE_CONTENT_URL)
                            .header(HttpHeaders.IF_UNMODIFIED_SINCE, formatInstant(headerDate)))
                    .andExpect(status().isPreconditionFailed());

            assertThat(getLastModified(INVOICE_CONTENT_URL)).isEqualTo(INVOICE_TIMESTAMP);
        }
    }

    @Nested
    class EmbeddedContentProperty {

        @Test
        void getCustomerContent_withRecentIfModifiedSince_http304() throws Exception {
            var headerDate = CUSTOMER_TIMESTAMP.plus(1, ChronoUnit.MINUTES);

            mockMvc.perform(get(CUSTOMER_CONTENT_URL)
                            .accept(MediaType.TEXT_PLAIN)
                            .header(HttpHeaders.IF_MODIFIED_SINCE, formatInstant(headerDate)))
                    .andExpect(status().isNotModified());
        }

        @Test
        void getCustomerContent_withOutdatedIfModifiedSince_http200() throws Exception {
            var headerDate = CUSTOMER_TIMESTAMP.minus(1, ChronoUnit.MINUTES);

            mockMvc.perform(get(CUSTOMER_CONTENT_URL)
                            .accept(MediaType.TEXT_PLAIN)
                            .header(HttpHeaders.IF_MODIFIED_SINCE, formatInstant(headerDate)))
                    .andExpect(status().isOk());
        }

        @Test
        void putCustomerContent_withRecentIfUnmodifiedSince_http204() throws Exception {
            var headerDate = CUSTOMER_TIMESTAMP.plus(1, ChronoUnit.MINUTES);

            // update content, ONLY changing the charset
            mockMvc.perform(put(CUSTOMER_CONTENT_URL)
                            .characterEncoding(StandardCharsets.UTF_8)
                            .contentType(MediaType.TEXT_PLAIN)
                            .content(EXT_ASCII_TEXT)
                            .header(HttpHeaders.IF_UNMODIFIED_SINCE, formatInstant(headerDate)))
                    .andExpect(status().isNoContent());

            assertThat(getLastModified(CUSTOMER_CONTENT_URL)).isAfterOrEqualTo(CUSTOMER_TIMESTAMP);
        }

        @Test
        void putCustomerContent_withOutdatedIfUnmodifiedSince_http412() throws Exception {
            var headerDate = CUSTOMER_TIMESTAMP.minus(1, ChronoUnit.MINUTES);

            // update content, ONLY changing the charset
            mockMvc.perform(put(CUSTOMER_CONTENT_URL)
                            .characterEncoding(StandardCharsets.UTF_8)
                            .contentType(MediaType.TEXT_PLAIN)
                            .content(EXT_ASCII_TEXT)
                            .header(HttpHeaders.IF_UNMODIFIED_SINCE, formatInstant(headerDate)))
                    .andExpect(status().isPreconditionFailed());

            assertThat(getLastModified(CUSTOMER_CONTENT_URL)).isEqualTo(CUSTOMER_TIMESTAMP);
        }

        @Test
        void deleteCustomerContent_withRecentIfUnmodifiedSince_http204() throws Exception {
            var headerDate = CUSTOMER_TIMESTAMP.plus(1, ChronoUnit.MINUTES);

            mockMvc.perform(delete(CUSTOMER_CONTENT_URL)
                            .header(HttpHeaders.IF_UNMODIFIED_SINCE, formatInstant(headerDate)))
                    .andExpect(status().isNoContent());
        }

        @Test
        void deleteCustomerContent_withOutdatedIfUnmodifiedSince_http412() throws Exception {
            var headerDate = CUSTOMER_TIMESTAMP.minus(1, ChronoUnit.MINUTES);

            mockMvc.perform(delete(CUSTOMER_CONTENT_URL)
                            .header(HttpHeaders.IF_UNMODIFIED_SINCE, formatInstant(headerDate)))
                    .andExpect(status().isPreconditionFailed());

            assertThat(getLastModified(CUSTOMER_CONTENT_URL)).isEqualTo(CUSTOMER_TIMESTAMP);
        }
    }
}
