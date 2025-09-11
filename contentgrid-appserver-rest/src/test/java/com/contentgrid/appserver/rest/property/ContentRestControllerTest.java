package com.contentgrid.appserver.rest.property;

import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.APPLICATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.query.engine.api.TableCreator;
import com.contentgrid.appserver.registry.SingleApplicationResolver;
import com.contentgrid.appserver.spring.test.WithMockJwt;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.tomcat.util.http.parser.ContentRange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "server.servlet.encoding.enabled=false", // disables mock-mvc enforcing charset in request
        "contentgrid.thunx.abac.source=none"
})
@AutoConfigureMockMvc
@WithMockJwt
class ContentRestControllerTest {

    private static final MockMultipartFile INVOICE_CONTENT_FILE = new MockMultipartFile(
            "content",
            "test-file.txt",
            "text/plain",
            "test content data".getBytes()
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TableCreator tableCreator;

    @MockitoSpyBean
    private ContentStore contentStoreSpy;

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        public SingleApplicationResolver singleApplicationResolver() {
            return new SingleApplicationResolver(APPLICATION);
        }
    }

    @BeforeEach
    void setup() {
        tableCreator.createTables(APPLICATION);
    }

    @AfterEach
    void teardown() {
        tableCreator.dropTables(APPLICATION);
    }

    private String createCustomer() throws Exception {
        return mockMvc.perform(post("/persons")
                        .formField("name", "Test person")
                        .formField("vat", UUID.randomUUID().toString())
                ).andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.LOCATION);
    }

    private String createInvoice(MockMultipartFile contentFile) throws Exception {
        String invoiceNumber = "INV-" + UUID.randomUUID().toString().substring(0, 8);

        var requestBuilder = multipart("/invoices");
        if (contentFile != null) {
            requestBuilder = requestBuilder.file(contentFile);
        }

        requestBuilder.param("number", invoiceNumber)
                .param("amount", "100.0")
                .param("customer", createCustomer())
                .param("confidentiality", "public");

        String responseContent = mockMvc.perform(requestBuilder)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // We don't care about content interactions done during setup
        Mockito.reset(contentStoreSpy);

        return objectMapper.readTree(responseContent).get("id").asText();
    }


    @ParameterizedTest
    @MethodSource("nonExistentPaths")
    void get_nonexistent_fails(String pathTemplate) throws Exception {
        String invoiceId = createInvoice(null);

        mockMvc.perform(get(pathTemplate, invoiceId))
                .andExpect(status().isNotFound());
    }

    static Stream<Arguments> nonExistentPaths() {
        return Stream.of(
                Arguments.argumentSet("non-existent ID", "/invoices/" + UUID.randomUUID() + "/content"),
                Arguments.argumentSet("non-existent entity", "/nonexistent/{instanceId}/content"),
                Arguments.argumentSet("non-existent property", "/invoices/{instanceId}/nonexistent")
        );
    }

    @Test
    void get_filename_success() throws Exception {
        String invoiceId = createInvoice(INVOICE_CONTENT_FILE);

        mockMvc.perform(get("/invoices/{instanceId}/content", invoiceId))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(content().contentType(INVOICE_CONTENT_FILE.getContentType()))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(INVOICE_CONTENT_FILE.getOriginalFilename(), StandardCharsets.UTF_8).build()
                        .toString()))
                .andExpect(content().bytes(INVOICE_CONTENT_FILE.getBytes()));

        Mockito.verify(contentStoreSpy).getReader(Mockito.any(), Mockito.assertArg(range -> {
            assertThat(range.getStartByte()).isEqualTo(0);
            assertThat(range.getRangeSize()).isEqualTo(INVOICE_CONTENT_FILE.getSize());
        }));
        Mockito.verifyNoMoreInteractions(contentStoreSpy);

    }

    @Test
    void get_nofilename_success() throws Exception {
        String invoiceId = createInvoice(new MockMultipartFile(
                INVOICE_CONTENT_FILE.getName(),
                null,
                INVOICE_CONTENT_FILE.getContentType(),
                INVOICE_CONTENT_FILE.getBytes()
        ) {
            @Override
            public String getOriginalFilename() {
                return null;
            }
        });

        mockMvc.perform(get("/invoices/{instanceId}/content", invoiceId))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(content().contentType(INVOICE_CONTENT_FILE.getContentType()))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment"))
                .andExpect(content().bytes(INVOICE_CONTENT_FILE.getBytes()));
    }

    @Test
    void get_if_none_match_notModified() throws Exception {
        String invoiceId = createInvoice(INVOICE_CONTENT_FILE);

        var eTag = mockMvc.perform(head("/invoices/{id}/content", invoiceId))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.ETAG);

        mockMvc.perform(get("/invoices/{id}/content", invoiceId)
                .header(HttpHeaders.IF_NONE_MATCH, eTag)
        ).andExpect(status().isNotModified());
    }

    @Test
    void get_if_none_match_ok() throws Exception {
        String invoiceId = createInvoice(INVOICE_CONTENT_FILE);
        mockMvc.perform(get("/invoices/{id}/content", invoiceId)
                        .header(HttpHeaders.IF_NONE_MATCH, "\"xyz\"")
                ).andExpect(status().isOk())
                .andExpect(content().contentType(INVOICE_CONTENT_FILE.getContentType()))
                .andExpect(content().bytes(INVOICE_CONTENT_FILE.getBytes()));
    }

    @Test
    void get_range_success() throws Exception {
        String invoiceId = createInvoice(INVOICE_CONTENT_FILE);

        mockMvc.perform(get("/invoices/{instanceId}/content", invoiceId)
                        .header(HttpHeaders.RANGE, "bytes=0-4"))
                .andExpect(status().isPartialContent())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(content().contentType(INVOICE_CONTENT_FILE.getContentType()))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-4/17"))
                .andExpect(content().bytes("test ".getBytes()));

        Mockito.verify(contentStoreSpy).getReader(Mockito.any(), Mockito.assertArg(range -> {
            assertThat(range.getStartByte()).isEqualTo(0);
            assertThat(range.getEndByteInclusive()).isEqualTo(4);
        }));
        Mockito.verifyNoMoreInteractions(contentStoreSpy);
    }

    @Test
    void get_range_if_match_success() throws Exception {
        String invoiceId = createInvoice(INVOICE_CONTENT_FILE);

        var eTag = mockMvc.perform(head("/invoices/{id}/content", invoiceId))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.ETAG);

        mockMvc.perform(get("/invoices/{instanceId}/content", invoiceId)
                        .header(HttpHeaders.RANGE, "bytes=0-4")
                        .header(HttpHeaders.IF_MATCH, eTag)
                ).andExpect(status().isPartialContent())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(content().contentType(INVOICE_CONTENT_FILE.getContentType()))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-4/17"));
    }

    @Test
    void get_range_if_match_fails() throws Exception {
        String invoiceId = createInvoice(INVOICE_CONTENT_FILE);

        mockMvc.perform(get("/invoices/{instanceId}/content", invoiceId)
                        .header(HttpHeaders.RANGE, "bytes=0-4")
                        .header(HttpHeaders.IF_MATCH, "\"my-value\"")
                ).andExpect(status().isPreconditionFailed());
    }

    public static Stream<Arguments> multi_range_requests() {
        return Stream.of(
                Arguments.argumentSet("Non-overlapping", "bytes=0-1,4-6", "bytes 0-1/17"),
                Arguments.argumentSet("Overlapping", "bytes=0-4,3-6", "bytes 0-4/17"),
                Arguments.argumentSet("Many Overlapping", "bytes=0-0,1-1,2-2,1-6", "bytes 0-0/17"),
                Arguments.argumentSet("Non-overlapping, start & suffix", "bytes=0-10,-10", "bytes 0-10/17"),
                Arguments.argumentSet("Non-overlapping, suffix & start", "bytes=-10,0-10", "bytes 7-16/17"),
                Arguments.argumentSet("Overlapping, start & suffix", "bytes=0-30,-10", "bytes 0-16/17"),
                Arguments.argumentSet("Overlapping, suffix & start", "bytes=-10,0-30", "bytes 7-16/17")
        );
    }

    @ParameterizedTest
    @MethodSource("multi_range_requests")
    void get_multi_range_success(String range, String contentRange) throws Exception {
        String invoiceId = createInvoice(INVOICE_CONTENT_FILE);

        mockMvc.perform(get("/invoices/{instanceId}/content", invoiceId)
                        .header(HttpHeaders.RANGE, range))
                .andExpect(status().isPartialContent())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(content().contentType(INVOICE_CONTENT_FILE.getContentType()))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, contentRange));

        Mockito.verify(contentStoreSpy).getReader(Mockito.any(), Mockito.assertArg(resolvedContentRange -> {
            var parsedRange = ContentRange.parse(new StringReader(contentRange));
            assertThat(resolvedContentRange.getStartByte()).isEqualTo(parsedRange.getStart());
            assertThat(resolvedContentRange.getEndByteInclusive()).isEqualTo(parsedRange.getEnd());
        }));
        Mockito.verifyNoMoreInteractions(contentStoreSpy);
    }

    @Test
    void get_suffix_range_success() throws Exception {
        String invoiceId = createInvoice(INVOICE_CONTENT_FILE);

        mockMvc.perform(get("/invoices/{instanceId}/content", invoiceId)
                        .header(HttpHeaders.RANGE, "bytes=-4"))
                .andExpect(status().isPartialContent())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(content().contentType(INVOICE_CONTENT_FILE.getContentType()))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 13-16/17"))
                .andExpect(content().bytes("data".getBytes()));

        Mockito.verify(contentStoreSpy).getReader(Mockito.any(), Mockito.assertArg(range -> {
            assertThat(range.getStartByte()).isEqualTo(13);
            assertThat(range.getEndByteInclusive()).isEqualTo(16);
        }));
        Mockito.verifyNoMoreInteractions(contentStoreSpy);
    }

    @Test
    void get_start_only_range_success() throws Exception {
        String invoiceId = createInvoice(INVOICE_CONTENT_FILE);

        mockMvc.perform(get("/invoices/{instanceId}/content", invoiceId)
                        .header(HttpHeaders.RANGE, "bytes=8-"))
                .andExpect(status().isPartialContent())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(content().contentType(INVOICE_CONTENT_FILE.getContentType()))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 8-16/17"))
                .andExpect(content().bytes("tent data".getBytes()));

        Mockito.verify(contentStoreSpy).getReader(Mockito.any(), Mockito.assertArg(range -> {
            assertThat(range.getStartByte()).isEqualTo(8);
            assertThat(range.getEndByteInclusive()).isEqualTo(16);
        }));
        Mockito.verifyNoMoreInteractions(contentStoreSpy);
    }

    @Test
    void get_range_start_oob_fails() throws Exception {
        String invoiceId = createInvoice(INVOICE_CONTENT_FILE);

        mockMvc.perform(get("/invoices/{instanceId}/content", invoiceId)
                        .header(HttpHeaders.RANGE, "bytes=100-200"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                // https://www.rfc-editor.org/rfc/rfc9110.html#field.content-range; unsatisfied-range
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes */17"));
        Mockito.verifyNoInteractions(contentStoreSpy);
    }

    @Test
    void get_range_end_oob_success() throws Exception {
        String invoiceId = createInvoice(INVOICE_CONTENT_FILE);

        mockMvc.perform(get("/invoices/{instanceId}/content", invoiceId)
                        .header(HttpHeaders.RANGE, "bytes=10-200"))
                .andExpect(status().isPartialContent())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(content().contentType(INVOICE_CONTENT_FILE.getContentType()))
                // https://www.rfc-editor.org/rfc/rfc9110.html#field.content-range; range-resp
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 10-16/17"))
                .andExpect(content().bytes("nt data".getBytes()));

        Mockito.verify(contentStoreSpy).getReader(Mockito.any(), Mockito.assertArg(range -> {
            assertThat(range.getStartByte()).isEqualTo(10);
            assertThat(range.getEndByteInclusive()).isEqualTo(16);
        }));
        Mockito.verifyNoMoreInteractions(contentStoreSpy);
    }

    @ParameterizedTest
    @CsvSource({
            "bytes=invalid",
            "bytes=10-5",
            "bytes=abc-def",
            "bytes=",
            "bytes=-",
            "invalid=0-10"
    })
    void get_invalid_range_header_fails(String rangeHeader) throws Exception {
        String invoiceId = createInvoice(INVOICE_CONTENT_FILE);

        mockMvc.perform(get("/invoices/{instanceId}/content", invoiceId)
                        .header(HttpHeaders.RANGE, rangeHeader))
                .andExpect(status().isBadRequest());
        Mockito.verifyNoInteractions(contentStoreSpy);
    }

    @Test
    void head_success() throws Exception {
        String invoiceId = createInvoice(INVOICE_CONTENT_FILE);

        mockMvc.perform(head("/invoices/{instanceId}/content", invoiceId))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(content().contentType(INVOICE_CONTENT_FILE.getContentType()))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(INVOICE_CONTENT_FILE.getOriginalFilename(), StandardCharsets.UTF_8).build()
                        .toString()))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, String.valueOf(INVOICE_CONTENT_FILE.getBytes().length)));

        // for a HEAD request, nothing got read from the content store
        Mockito.verifyNoInteractions(contentStoreSpy);
    }

    @ParameterizedTest
    @MethodSource("nonExistentPaths")
    void head_nonexistent_fails(String uriTemplate) throws Exception {
        String invoiceId = createInvoice(null);

        mockMvc.perform(head(uriTemplate, invoiceId))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist(HttpHeaders.ETAG));
    }

    @Test
    void head_range_success() throws Exception {
        String invoiceId = createInvoice(INVOICE_CONTENT_FILE);

        mockMvc.perform(head("/invoices/{instanceId}/content", invoiceId)
                        .header(HttpHeaders.RANGE, "bytes=0-4"))
                .andExpect(status().isPartialContent())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(content().contentType(INVOICE_CONTENT_FILE.getContentType()))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-4/17"))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "5"));

        // for a HEAD request, nothing got read from the content store
        Mockito.verifyNoInteractions(contentStoreSpy);
    }

    @ParameterizedTest
    @CsvSource({"PUT", "POST"})
    void upload_plain_success(HttpMethod method) throws Exception {
        String invoiceId = createInvoice(null);

        mockMvc.perform(request(method, "/invoices/{instanceId}/content", invoiceId)
                        .contentType(INVOICE_CONTENT_FILE.getContentType())
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + INVOICE_CONTENT_FILE.getOriginalFilename() + "\"")
                        .content(INVOICE_CONTENT_FILE.getBytes()))
                .andExpect(status().isNoContent())
                .andExpect(header().exists(HttpHeaders.ETAG));

        mockMvc.perform(get("/invoices/{instanceId}/content", invoiceId))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(INVOICE_CONTENT_FILE.getOriginalFilename(), StandardCharsets.UTF_8).build()
                        .toString()))
                .andExpect(content().bytes(INVOICE_CONTENT_FILE.getBytes()));
    }

    @ParameterizedTest
    @CsvSource({"PUT", "POST"})
    void upload_plain_no_filename_success(HttpMethod method) throws Exception {
        String invoiceId = createInvoice(null);

        mockMvc.perform(request(method, "/invoices/{instanceId}/content", invoiceId)
                        .contentType(INVOICE_CONTENT_FILE.getContentType())
                        .content(INVOICE_CONTENT_FILE.getBytes()))
                .andExpect(status().isNoContent())
                .andExpect(header().exists(HttpHeaders.ETAG));

        mockMvc.perform(get("/invoices/{instanceId}/content", invoiceId))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment"))
                .andExpect(content().bytes(INVOICE_CONTENT_FILE.getBytes()));
    }

    @ParameterizedTest
    @CsvSource({"PUT", "POST"})
    void upload_multipart_success(HttpMethod method) throws Exception {
        String invoiceId = createInvoice(null);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                INVOICE_CONTENT_FILE.getOriginalFilename(),
                INVOICE_CONTENT_FILE.getContentType(),
                INVOICE_CONTENT_FILE.getBytes()
        );

        mockMvc.perform(multipart(method, "/invoices/{instanceId}/content", invoiceId)
                        .file(file))
                .andExpect(status().isNoContent())
                .andExpect(header().exists(HttpHeaders.ETAG));

        mockMvc.perform(get("/invoices/{instanceId}/content", invoiceId))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(INVOICE_CONTENT_FILE.getOriginalFilename(), StandardCharsets.UTF_8).build()
                        .toString()))
                .andExpect(content().bytes(INVOICE_CONTENT_FILE.getBytes()));
    }

    @ParameterizedTest
    @CsvSource({"PUT", "POST"})
    void upload_update_content_success(HttpMethod method) throws Exception {
        String invoiceId = createInvoice(INVOICE_CONTENT_FILE);

        mockMvc.perform(request(method, "/invoices/{instanceId}/content", invoiceId)
                        .contentType("application/json")
                        .content("updated content"))
                .andExpect(status().isNoContent())
                .andExpect(header().exists(HttpHeaders.ETAG));

        mockMvc.perform(get("/invoices/{instanceId}/content", invoiceId))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(content().contentType("application/json"))
                .andExpect(content().bytes("updated content".getBytes()));
    }

    @ParameterizedTest
    @MethodSource("nonExistentPaths")
    void upload_nonexistent_fails(String pathTemplate) throws Exception {
        String instanceId = createInvoice(null);

        mockMvc.perform(post(pathTemplate, instanceId)
                        .contentType(INVOICE_CONTENT_FILE.getContentType())
                        .content(INVOICE_CONTENT_FILE.getBytes()))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @MethodSource("nonExistentPaths")
    void upload_nonexistent_multipart_fails(String pathTemplate) throws Exception {
        String instanceId = createInvoice(null);

        mockMvc.perform(multipart(pathTemplate, instanceId)
                        .file(new MockMultipartFile(
                                "file",
                                INVOICE_CONTENT_FILE.getOriginalFilename(),
                                INVOICE_CONTENT_FILE.getContentType(),
                                INVOICE_CONTENT_FILE.getInputStream()
                        ))
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void upload_no_content_type_fails() throws Exception {
        String invoiceId = createInvoice(null);

        mockMvc.perform(post("/invoices/{instanceId}/content", invoiceId)
                        .content(INVOICE_CONTENT_FILE.getBytes()))
                .andExpect(status().isBadRequest());

        Mockito.verifyNoInteractions(contentStoreSpy);

        mockMvc.perform(get("/invoices/{instanceId}/content", invoiceId))
                .andExpect(status().isNotFound());
    }

    @Test
    void upload_empty_content_type_fails() throws Exception {
        String invoiceId = createInvoice(null);

        mockMvc.perform(post("/invoices/{instanceId}/content", invoiceId)
                        .contentType("")
                        .content(INVOICE_CONTENT_FILE.getBytes()))
                .andExpect(status().isBadRequest());

        Mockito.verifyNoInteractions(contentStoreSpy);

        // No upload has happened
        mockMvc.perform(get("/invoices/{instanceId}/content", invoiceId))
                .andExpect(status().isNotFound());

    }

    @Test
    void upload_if_none_match_wildcard_succeeds() throws Exception {
        String invoiceId = createInvoice(null);

        var createEtag = mockMvc.perform(put("/invoices/{id}/content", invoiceId)
                .contentType(INVOICE_CONTENT_FILE.getContentType())
                .content(INVOICE_CONTENT_FILE.getBytes())
                .header("If-None-Match", "*")
        ).andExpect(status().isNoContent())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.ETAG);

        mockMvc.perform(get("/invoices/{id}/content", invoiceId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, createEtag));
    }

    @Test
    void upload_if_match_succeeds() throws Exception {
        String invoiceId = createInvoice(INVOICE_CONTENT_FILE);
        var existingEtag = mockMvc.perform(head("/invoices/{id}/content", invoiceId))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.ETAG);

        var updateEtag = mockMvc.perform(post("/invoices/{id}/content", invoiceId)
                        .contentType(INVOICE_CONTENT_FILE.getContentType())
                        .content(INVOICE_CONTENT_FILE.getBytes())
                        .header("If-Match", existingEtag)
                ).andExpect(status().isNoContent())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.ETAG);

        // New content upload changes the ETag
        assertThat(updateEtag).isNotEqualTo(existingEtag);

        mockMvc.perform(get("/invoices/{id}/content", invoiceId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, updateEtag));
    }

    @ParameterizedTest
    @CsvSource({
            "If-Match,\"some-value\"",
            "If-None-Match,*"
    })
    void upload_etag_fails(String header, String headerValue) throws Exception {
        String invoiceId = createInvoice(INVOICE_CONTENT_FILE);

        mockMvc.perform(post("/invoices/{id}/content", invoiceId)
                        .contentType(INVOICE_CONTENT_FILE.getContentType())
                        .content(INVOICE_CONTENT_FILE.getBytes())
                        .header(header, headerValue))
                .andExpect(status().isPreconditionFailed());

        Mockito.verifyNoInteractions(contentStoreSpy);
    }

    @Test
    void upload_custom_content_type_success() throws Exception {
        String invoiceId = createInvoice(null);
        String customContentType = "application/vnd.custom+json";

        mockMvc.perform(post("/invoices/{instanceId}/content", invoiceId)
                        .contentType(customContentType)
                        .content("{\"custom\": \"data\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/invoices/{instanceId}/content", invoiceId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(customContentType))
                .andExpect(content().bytes("{\"custom\": \"data\"}".getBytes()));
    }

    @Test
    void upload_multipart_no_content_type_fails() throws Exception {
        String invoiceId = createInvoice(null);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                INVOICE_CONTENT_FILE.getOriginalFilename(),
                null, // No content type
                INVOICE_CONTENT_FILE.getBytes()
        );

        mockMvc.perform(multipart("/invoices/{instanceId}/content", invoiceId)
                        .file(file))
                .andExpect(status().isBadRequest());

        Mockito.verifyNoInteractions(contentStoreSpy);

        mockMvc.perform(get("/invoices/{instanceId}/content", invoiceId))
                .andExpect(status().isNotFound());
    }


    @Test
    void upload_multipart_no_file_fails() throws Exception {
        String invoiceId = createInvoice(null);

        mockMvc.perform(multipart("/invoices/{instanceId}/content", invoiceId))
                .andExpect(status().isBadRequest());

        Mockito.verifyNoInteractions(contentStoreSpy);

        mockMvc.perform(get("/invoices/{instanceId}/content", invoiceId))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_success() throws Exception {
        String invoiceId = createInvoice(INVOICE_CONTENT_FILE);

        // Verify content exists
        mockMvc.perform(get("/invoices/{instanceId}/content", invoiceId))
                .andExpect(status().isOk());

        // Delete content
        mockMvc.perform(delete("/invoices/{instanceId}/content", invoiceId))
                .andExpect(status().isNoContent());

        // Verify content is gone
        mockMvc.perform(get("/invoices/{instanceId}/content", invoiceId))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @MethodSource("nonExistentPaths")
    void delete_nonexistent_fails(String pathTemplate) throws Exception {
        String instanceId = createInvoice(null);

        mockMvc.perform(delete(pathTemplate, instanceId))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_no_content_success() throws Exception {
        String invoiceId = createInvoice(null);

        // Try to delete content that doesn't exist
        mockMvc.perform(delete("/invoices/{instanceId}/content", invoiceId))
                .andExpect(status().isNoContent()); // Should succeed even if content doesn't exist
    }
}
