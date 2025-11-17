package com.contentgrid.appserver.integration.test.auditing;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.integration.test.auditing.AuditMetadataTest.TestClockConfiguration;
import com.contentgrid.appserver.integration.test.fixture.invoicing.InvoicingApi;
import com.contentgrid.appserver.integration.test.fixture.invoicing.InvoicingApiApplication;
import com.contentgrid.appserver.rest.test.WithMockJwt;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriTemplate;

@Slf4j
@SpringBootTest(properties = {
        "contentgrid.events.rabbitmq.enabled=false",
})
@ContextConfiguration(classes = {
        InvoicingApiApplication.class,
        TestClockConfiguration.class,
})
@AutoConfigureMockMvc(printOnlyOnFailure = false)
@WithMockJwt(subject = "user-id-1", name = "John", issuer = AuditMetadataTest.JWT_ISSUER_NAMESPACE)
public class AuditMetadataTest {

    static final String INVOICE_NUMBER_1 = "I-2022-0001";
    static final String ORG_XENIT_VAT = "BE0887582365";
    static final String JWT_ISSUER_NAMESPACE = "http://localhost/realms/cg-invalid";

    static EntityId XENIT_ID;
    static EntityId INVOICE_1_ID;

    private static final Instant TIMESTAMP = Instant.parse("2025-11-14T13:56:44Z");
    private static final Instant CONTENT_TIMESTAMP = Instant.parse("2025-11-14T13:58:12Z");

    private static final String EXT_ASCII_TEXT = "L'éducation doit être gratuite.";
    private static final String UNICODE_TEXT = "Some unicode text 💩";
    private static final String MIMETYPE_PLAINTEXT_LATIN1 = "text/plain;charset=ISO-8859-1";
    private static final String MIMETYPE_PLAINTEXT_UTF8 = "text/plain;charset=UTF-8";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InvoicingApi invoicingApi;

    @Autowired
    TestClock testClock;

    @TestConfiguration
    static class TestClockConfiguration {
        @Bean
        Clock testClock() {
            return new TestClock(TIMESTAMP);
        }
    }

    @RequiredArgsConstructor
    static class TestClock extends Clock {

        @NonNull
        @Setter
        private Instant timestamp;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(timestamp, zone);
        }

        @Override
        public Instant instant() {
            return timestamp;
        }
    }

    String formatInstant(Instant date) {
        DateTimeFormatter formatter = DateTimeFormatter.RFC_1123_DATE_TIME
                .withZone(ZoneId.of("GMT"));
        return formatter.format(date);
    }

    static JwtRequestPostProcessor jwtWithClaims(String subject, String name) {
        return jwt().jwt(jwt -> jwt
                .subject(subject)
                .claim("name", name)
                .issuer(JWT_ISSUER_NAMESPACE)
        );
    }

    @BeforeEach
    void setupTestData() throws InvalidPropertyDataException {
        testClock.setTimestamp(TIMESTAMP);
        var xenit = invoicingApi.createCustomer("XeniT", ORG_XENIT_VAT);

        XENIT_ID = xenit.getIdentity().getEntityId();

        INVOICE_1_ID = invoicingApi.createInvoice(INVOICE_NUMBER_1, true, false, XENIT_ID, new HashSet<>())
                .getIdentity().getEntityId();
    }

    void setupContentProperties() throws InvalidPropertyDataException {
        testClock.setTimestamp(CONTENT_TIMESTAMP);
        var stream = new ByteArrayInputStream(EXT_ASCII_TEXT.getBytes(StandardCharsets.ISO_8859_1));

        invoicingApi.storeCustomerContent(XENIT_ID, "content.txt", MIMETYPE_PLAINTEXT_LATIN1, stream);
        invoicingApi.storeInvoiceContent(INVOICE_1_ID, "content.txt", MIMETYPE_PLAINTEXT_LATIN1, stream);
    }

    @AfterEach
    void cleanupTestData() {
        invoicingApi.deleteAll();
    }

    void checkInvoiceAuditMetadataFields(EntityId id, String createdBy, Instant createdDate) throws Exception {
        checkInvoiceAuditMetadataFields(id, createdBy, createdDate, createdBy, createdDate);
    }

    void checkInvoiceAuditMetadataFields(
            EntityId id, String createdBy, Instant createdDate, String lastModifiedBy, Instant lastModifiedDate
    ) throws Exception {
        checkAuditMetadataFields("invoices", id, createdBy, createdDate, lastModifiedBy, lastModifiedDate);
    }

    void checkCustomerAuditMetadataFields(EntityId id, String createdBy, Instant createdDate) throws Exception {
        checkCustomerAuditMetadataFields(id, createdBy, createdDate, createdBy, createdDate);
    }

    void checkCustomerAuditMetadataFields(
            EntityId id, String createdBy, Instant createdDate, String lastModifiedBy, Instant lastModifiedDate
    ) throws Exception {
        checkAuditMetadataFields("customers", id, createdBy, createdDate, lastModifiedBy, lastModifiedDate);
    }

    void checkAuditMetadataFields(String repository, EntityId id, String createdBy, Instant createdDate,
            String lastModifiedBy, Instant lastModifiedDate) throws Exception {
        mockMvc.perform(get("/{repository}/{id}", repository, id))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                            audit_metadata: {
                                created_by: "%s",
                                created_date: "%s",
                                last_modified_by: "%s",
                                last_modified_date: "%s"
                            }
                        }
                        """.formatted(createdBy, createdDate, lastModifiedBy, lastModifiedDate)))
                .andExpect(header().dateValue(HttpHeaders.LAST_MODIFIED, lastModifiedDate.toEpochMilli()));
    }

    @Nested
    class ItemResource {

        @Test
        void getInvoice_withOutdatedIfModifiedSince_http200() throws Exception {
            var headerDate = TIMESTAMP.minus(1, ChronoUnit.HOURS);

            mockMvc.perform(get("/invoices/" + INVOICE_1_ID)
                            .accept(MediaType.APPLICATION_JSON)
                            .header(HttpHeaders.IF_MODIFIED_SINCE, formatInstant(headerDate)))
                    .andExpect(status().isOk());
        }

        @Test
        void getInvoice_withRecentIfModifiedSince_http304() throws Exception {
            var headerDate = TIMESTAMP.plus(1, ChronoUnit.HOURS);

            mockMvc.perform(get("/invoices/" + INVOICE_1_ID)
                            .accept(MediaType.APPLICATION_JSON)
                            .header(HttpHeaders.IF_MODIFIED_SINCE, formatInstant(headerDate)))
                    .andExpect(status().isNotModified());
        }

        @Test
        void getInvoice_withOutdatedIfUnmodifiedSince_http412() throws Exception {
            var headerDate = TIMESTAMP.minus(1, ChronoUnit.HOURS);

            mockMvc.perform(get("/invoices/" + INVOICE_1_ID)
                            .accept(MediaType.APPLICATION_JSON)
                            .header(HttpHeaders.IF_UNMODIFIED_SINCE, formatInstant(headerDate)))
                    .andExpect(status().isPreconditionFailed());
        }

        @Test
        void getInvoice_withRecentIfUnmodifiedSince_http200() throws Exception {
            var headerDate = TIMESTAMP.plus(1, ChronoUnit.HOURS);

            mockMvc.perform(get("/invoices/" + INVOICE_1_ID)
                            .accept(MediaType.APPLICATION_JSON)
                            .header(HttpHeaders.IF_UNMODIFIED_SINCE, formatInstant(headerDate)))
                    .andExpect(status().isOk());
        }

        @Test
        void postInvoice_shouldSetAuditMetadataFields() throws Exception {
            var createdDate = TIMESTAMP.plus(1, ChronoUnit.HOURS);
            testClock.setTimestamp(createdDate);

            var response = mockMvc.perform(post("/invoices")
                            .with(jwtWithClaims("user-id-2", "Bob"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "number": "I-2022-0003",
                                        "counterparty": "/customers/%s"
                                    }
                                    """.formatted(XENIT_ID)))
                    .andExpect(status().isCreated())
                    .andReturn();

            var location = Objects.requireNonNull(response.getResponse().getHeader(HttpHeaders.LOCATION));
            var matches = new UriTemplate("{scheme}://{host}/invoices/{id}").match(location);
            var invoiceId = matches.get("id");

            checkInvoiceAuditMetadataFields(EntityId.of(UUID.fromString(invoiceId)), "Bob", createdDate);
        }

        @Test
        @Disabled("ACC-1220")
        void putInvoice_withOutdatedIfUnmodifiedSince_http412() throws Exception {
            var modifiedDate = TIMESTAMP.plus(1, ChronoUnit.HOURS);
            testClock.setTimestamp(modifiedDate);

            var headerDate = TIMESTAMP.minus(1, ChronoUnit.MINUTES);

            mockMvc.perform(put("/invoices/{id}", INVOICE_1_ID)
                            .with(jwtWithClaims("user-id-2", "Bob"))
                            .header(HttpHeaders.IF_UNMODIFIED_SINCE, formatInstant(headerDate))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "number": "%s",
                                        "paid": true
                                    }
                                    """.formatted(INVOICE_NUMBER_1)))
                    .andExpect(status().isPreconditionFailed());

            checkInvoiceAuditMetadataFields(INVOICE_1_ID, "John", TIMESTAMP);
        }

        @Test
        void putInvoice_withRecentIfUnmodifiedSince_http200() throws Exception {
            var modifiedDate = TIMESTAMP.plus(1, ChronoUnit.HOURS);
            testClock.setTimestamp(modifiedDate);

            var headerDate = TIMESTAMP.plus(1, ChronoUnit.MINUTES);

            mockMvc.perform(put("/invoices/{id}", INVOICE_1_ID)
                            .with(jwtWithClaims("user-id-2", "Bob"))
                            .header(HttpHeaders.IF_UNMODIFIED_SINCE, formatInstant(headerDate))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "number": "%s",
                                        "paid": true
                                    }
                                    """.formatted(INVOICE_NUMBER_1)))
                    .andExpect(status().isNoContent());

            checkInvoiceAuditMetadataFields(INVOICE_1_ID, "John", TIMESTAMP, "Bob", modifiedDate);
        }

        @Test
        @Disabled("ACC-1220")
        void deleteInvoice_withOutdatedIfUnmodifiedSince_http412() throws Exception {
            var headerDate = TIMESTAMP.minus(1, ChronoUnit.MINUTES);

            mockMvc.perform(delete("/invoices/{id}", INVOICE_1_ID)
                            .with(jwtWithClaims("user-id-2", "Bob"))
                            .header(HttpHeaders.IF_UNMODIFIED_SINCE, formatInstant(headerDate)))
                    .andExpect(status().isPreconditionFailed());

            checkInvoiceAuditMetadataFields(INVOICE_1_ID, "John", TIMESTAMP);
        }

        @Test
        void deleteInvoice_withRecentIfUnmodifiedSince_http204() throws Exception {
            var headerDate = TIMESTAMP.plus(1, ChronoUnit.MINUTES);

            mockMvc.perform(delete("/invoices/{id}", INVOICE_1_ID)
                            .with(jwtWithClaims("user-id-2", "Bob"))
                            .header(HttpHeaders.IF_UNMODIFIED_SINCE, formatInstant(headerDate)))
                    .andExpect(status().isNoContent());
        }
    }

    @Nested
    class InlinedContentProperty {

        @Test
        void postInvoiceContent_shouldUpdateAuditMetadataFields_http201() throws Exception {
            var modifiedDate = TIMESTAMP.plus(1, ChronoUnit.HOURS);
            testClock.setTimestamp(modifiedDate);

            mockMvc.perform(post("/invoices/{id}/content", INVOICE_1_ID)
                            .with(jwtWithClaims("user-id-2", "Bob"))
                            .contentType(MediaType.TEXT_PLAIN)
                            .characterEncoding(StandardCharsets.UTF_8)
                            .content(UNICODE_TEXT))
                    .andExpect(status().isCreated());

            checkInvoiceAuditMetadataFields(INVOICE_1_ID, "John", TIMESTAMP, "Bob", modifiedDate);
        }

        @Test
        void postMultipartInvoiceAndContent_shouldSetAuditMetadataFields_http201() throws Exception {
            var createdDate = TIMESTAMP.plus(1, ChronoUnit.HOURS);
            testClock.setTimestamp(createdDate);
            var file = new MockMultipartFile("content", "content.txt", MIMETYPE_PLAINTEXT_UTF8,
                    UNICODE_TEXT.getBytes(StandardCharsets.UTF_8));

            var response = mockMvc.perform(multipart(HttpMethod.POST, "/invoices")
                            .file(file)
                            .with(jwtWithClaims("user-id-2", "Bob"))
                            .param("number", "I-2022-0003")
                            .param("counterparty", "/customers/" + XENIT_ID))
                    .andExpect(status().isCreated())
                    .andReturn();

            var location = Objects.requireNonNull(response.getResponse().getHeader(HttpHeaders.LOCATION));
            var matches = new UriTemplate("{scheme}://{host}/invoices/{id}").match(location);
            var invoiceId = matches.get("id");

            checkInvoiceAuditMetadataFields(EntityId.of(UUID.fromString(invoiceId)), "Bob", createdDate);
        }

        @Test
        void putInvoiceContent_shouldUpdateAuditMetadataFields_http200() throws Exception {
            setupContentProperties();
            var modifiedDate = CONTENT_TIMESTAMP.plus(1, ChronoUnit.HOURS);
            testClock.setTimestamp(modifiedDate);

            // update content, ONLY changing the charset
            mockMvc.perform(put("/invoices/{id}/content", INVOICE_1_ID)
                            .with(jwtWithClaims("user-id-2", "Bob"))
                            .characterEncoding(StandardCharsets.UTF_8)
                            .contentType(MediaType.TEXT_PLAIN)
                            .content(EXT_ASCII_TEXT))
                    .andExpect(status().isOk());

            checkInvoiceAuditMetadataFields(INVOICE_1_ID, "John", TIMESTAMP, "Bob", modifiedDate);
        }

        @Test
        void deleteInvoiceContent_shouldUpdateAuditMetadataFields_http204() throws Exception {
            setupContentProperties();
            var modifiedDate = CONTENT_TIMESTAMP.plus(1, ChronoUnit.HOURS);
            testClock.setTimestamp(modifiedDate);

            mockMvc.perform(delete("/invoices/{id}/content", INVOICE_1_ID)
                            .with(jwtWithClaims("user-id-2", "Bob")))
                    .andExpect(status().isNoContent());

            checkInvoiceAuditMetadataFields(INVOICE_1_ID, "John", TIMESTAMP, "Bob", modifiedDate);
        }
    }

    @Nested
    class EmbeddedContentProperty {

        @Test
        void postCustomerContent_shouldUpdateAuditMetadataFields_http201() throws Exception {
            var modifiedDate = TIMESTAMP.plus(1, ChronoUnit.HOURS);
            testClock.setTimestamp(modifiedDate);

            mockMvc.perform(post("/customers/{id}/content", XENIT_ID)
                            .with(jwtWithClaims("user-id-2", "Bob"))
                            .contentType(MediaType.TEXT_PLAIN)
                            .characterEncoding(StandardCharsets.UTF_8)
                            .content(UNICODE_TEXT))
                    .andExpect(status().isCreated());

            checkCustomerAuditMetadataFields(XENIT_ID, "John", TIMESTAMP, "Bob", modifiedDate);
        }

        @Test
        void postMultipartCustomerAndContent_shouldSetAuditMetadataFields_http201() throws Exception {
            var createdDate = TIMESTAMP.plus(1, ChronoUnit.HOURS);
            testClock.setTimestamp(createdDate);
            var file = new MockMultipartFile("content", "content.txt", MIMETYPE_PLAINTEXT_UTF8,
                    UNICODE_TEXT.getBytes(StandardCharsets.UTF_8));

            var response = mockMvc.perform(multipart(HttpMethod.POST, "/customers")
                            .file(file)
                            .with(jwtWithClaims("user-id-2", "Bob"))
                            .param("name", "Example")
                            .param("vat", "BE_EXAMPLE"))
                    .andExpect(status().isCreated())
                    .andReturn();

            var location = Objects.requireNonNull(response.getResponse().getHeader(HttpHeaders.LOCATION));
            var matches = new UriTemplate("{scheme}://{host}/customers/{id}").match(location);
            var customerId = matches.get("id");

            checkCustomerAuditMetadataFields(EntityId.of(UUID.fromString(customerId)), "Bob", createdDate);
        }

        @Test
        void putCustomerContent_shouldUpdateAuditMetadataFields_http200() throws Exception {
            setupContentProperties();
            var modifiedDate = CONTENT_TIMESTAMP.plus(1, ChronoUnit.HOURS);
            testClock.setTimestamp(modifiedDate);

            // update content, ONLY changing the charset
            mockMvc.perform(put("/customers/{id}/content", XENIT_ID)
                            .with(jwtWithClaims("user-id-2", "Bob"))
                            .characterEncoding(StandardCharsets.UTF_8)
                            .contentType(MediaType.TEXT_PLAIN)
                            .content(EXT_ASCII_TEXT))
                    .andExpect(status().isOk());

            checkCustomerAuditMetadataFields(XENIT_ID, "John", TIMESTAMP, "Bob", modifiedDate);
        }

        @Test
        void deleteCustomerContent_shouldUpdateAuditMetadataFields_http204() throws Exception {
            setupContentProperties();
            var modifiedDate = CONTENT_TIMESTAMP.plus(1, ChronoUnit.HOURS);
            testClock.setTimestamp(modifiedDate);

            mockMvc.perform(delete("/customers/{id}/content", XENIT_ID)
                            .with(jwtWithClaims("user-id-2", "Bob")))
                    .andExpect(status().isNoContent());

            checkCustomerAuditMetadataFields(XENIT_ID, "John", TIMESTAMP, "Bob", modifiedDate);
        }
    }
}
