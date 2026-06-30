package com.contentgrid.appserver.rest.entity;

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

import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.contentstore.api.UnwritableContentException;
import com.contentgrid.appserver.domain.content.ContentStoreResolver;
import com.contentgrid.appserver.contentstore.impl.utils.testing.MockContentStore;
import com.contentgrid.appserver.rest.test.TestApplication;
import com.contentgrid.appserver.query.engine.api.TableCreator;
import com.contentgrid.appserver.rest.test.ProblemDetailsMockMvcMatchers;
import org.junit.jupiter.api.AutoClose;
import tools.jackson.databind.json.JsonMapper;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.tomcat.util.http.parser.ContentRange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = TestApplication.class, properties = {
        "spring.servlet.encoding.enabled=false", // disables mock-mvc enforcing charset in request
})
@AutoConfigureMockMvc
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
    private JsonMapper jsonMapper;

    @Autowired
    private TableCreator tableCreator;

    @MockitoBean
    private ContentStoreResolver contentStoreResolverMock;

    @AutoClose
    private final ContentStore realContentStore = new MockContentStore();
    private ContentStore contentStoreSpy;

    @BeforeEach
    void setup() {
        tableCreator.createTables(APPLICATION);
        contentStoreSpy = Mockito.spy(realContentStore);
        Mockito.when(contentStoreResolverMock.resolve(Mockito.any())).thenReturn(contentStoreSpy);
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
        String invoiceNumber = String.valueOf(new Random().nextLong(0, Long.MAX_VALUE));

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

        return jsonMapper.readTree(responseContent).get("id").asText();
    }

    static Stream<Arguments> nonExistentPaths() {
        return Stream.of(
                Arguments.argumentSet("non-existent ID", "/invoices/" + UUID.randomUUID() + "/content",
                        "https://contentgrid.cloud/problems/not-found/entity-item"),
                Arguments.argumentSet("invalid ID format", "/invoices/invalid-id/content",
                        "https://contentgrid.cloud/problems/not-found/endpoint"),
                Arguments.argumentSet("non-existent entity", "/nonexistent/{instanceId}/content",
                        "https://contentgrid.cloud/problems/not-found/endpoint"),
                Arguments.argumentSet("non-existent property", "/invoices/{instanceId}/nonexistent",
                        "https://contentgrid.cloud/problems/not-found/endpoint")
        );
    }


    @Nested
    class GetContent {

        @ParameterizedTest
        @MethodSource("com.contentgrid.appserver.rest.entity.ContentRestControllerTest#nonExistentPaths")
        void get_nonexistent_fails(String pathTemplate, String problemType) throws Exception {
            String invoiceId = createInvoice(null);

            mockMvc.perform(get(pathTemplate, invoiceId))
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                            .withStatusCode(HttpStatus.NOT_FOUND)
                            .withType(problemType)
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
                assertThat(range).isNull();
            }));
            Mockito.verifyNoMoreInteractions(contentStoreSpy);

        }

        @Test
        void get_no_filename_success() throws Exception {
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
        void get_empty_file_success() throws Exception {
            String invoiceId = createInvoice(new MockMultipartFile(
                    INVOICE_CONTENT_FILE.getName(),
                    INVOICE_CONTENT_FILE.getOriginalFilename(),
                    INVOICE_CONTENT_FILE.getContentType(),
                    InputStream.nullInputStream()
            ));

            mockMvc.perform(get("/invoices/{instanceId}/content", invoiceId))
                    .andExpect(status().isOk())
                    .andExpect(header().exists(HttpHeaders.ETAG))
                    .andExpect(content().contentType(INVOICE_CONTENT_FILE.getContentType()))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(INVOICE_CONTENT_FILE.getOriginalFilename(), StandardCharsets.UTF_8).build()
                            .toString()))
                    .andExpect(content().bytes(new byte[0]));
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
        void get_if_match_ok() throws Exception {
            String invoiceId = createInvoice(INVOICE_CONTENT_FILE);

            var eTag = mockMvc.perform(head("/invoices/{id}/content", invoiceId))
                    .andExpect(status().isOk())
                    .andExpect(header().exists(HttpHeaders.ETAG))
                    .andReturn()
                    .getResponse()
                    .getHeader(HttpHeaders.ETAG);

            mockMvc.perform(get("/invoices/{id}/content", invoiceId)
                            .header(HttpHeaders.IF_MATCH, eTag))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(INVOICE_CONTENT_FILE.getContentType()))
                    .andExpect(content().bytes(INVOICE_CONTENT_FILE.getBytes()));
        }

        @Test
        void get_if_match_precondition_failed() throws Exception {
            String invoiceId = createInvoice(INVOICE_CONTENT_FILE);
            mockMvc.perform(get("/invoices/{id}/content", invoiceId)
                            .header(HttpHeaders.IF_MATCH, "\"xyz\""))
                    .andExpect(status().isPreconditionFailed());
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
                    )
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                            .withStatusCode(HttpStatus.PRECONDITION_FAILED)
                            .withType("https://contentgrid.cloud/problems/unsatisfied-version")
                            .withTitle("Object has changed")
                            .withSatisfy(pd -> {
                                assertThat(pd.getDetail()).isEqualTo(
                                        "The requested object is now exactly '%s', which does not match requested is any of [exactly 'my-value']"
                                                .formatted(pd.getProperties().get("actual_version"))
                                );
                            })
                            .withField("actual_version", v -> assertThat(v).asString().matches("[a-z0-9]+"))
                    );
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

        @ParameterizedTest
        @CsvSource({"100-200", "17-"})
        void get_range_start_oob_fails(String bytes) throws Exception {
            String invoiceId = createInvoice(INVOICE_CONTENT_FILE);

            mockMvc.perform(get("/invoices/{instanceId}/content", invoiceId)
                            .header(HttpHeaders.RANGE, "bytes=" + bytes))
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
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                            .withStatusCode(HttpStatus.BAD_REQUEST)
                            .withSatisfy(pd -> assertThat(pd.getDetail())
                                    .isIn("Can not parse Range header", "At least one range specifier is required")
                            ));
            Mockito.verifyNoInteractions(contentStoreSpy);
        }

        @Test
        void get_empty_file_with_range_fails() throws Exception {
            String invoiceId = createInvoice(new MockMultipartFile(
                    INVOICE_CONTENT_FILE.getName(),
                    INVOICE_CONTENT_FILE.getOriginalFilename(),
                    INVOICE_CONTENT_FILE.getContentType(),
                    InputStream.nullInputStream()
            ));

            mockMvc.perform(get("/invoices/{instanceId}/content", invoiceId)
                            .header(HttpHeaders.RANGE, "bytes=0-4"))
                    .andExpect(status().isRequestedRangeNotSatisfiable())
                    // https://www.rfc-editor.org/rfc/rfc9110.html#field.content-range; unsatisfied-range
                    .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes */0"));
            Mockito.verifyNoInteractions(contentStoreSpy);
        }

        @Test
        @Disabled("Spring returns 416 Response")
        void get_empty_file_with_suffix_range_succeeds() throws Exception {
            // https://www.rfc-editor.org/rfc/rfc9110.html#name-byte-ranges
            // When a selected representation has zero length, the only satisfiable form
            // of range-spec in a GET request is a suffix-range with a non-zero suffix-length.
            String invoiceId = createInvoice(new MockMultipartFile(
                    INVOICE_CONTENT_FILE.getName(),
                    INVOICE_CONTENT_FILE.getOriginalFilename(),
                    INVOICE_CONTENT_FILE.getContentType(),
                    InputStream.nullInputStream()
            ));

            mockMvc.perform(get("/invoices/{instanceId}/content", invoiceId)
                            .header(HttpHeaders.RANGE, "bytes=-1"))
                    .andExpect(status().isOk())
                    .andExpect(header().exists(HttpHeaders.ETAG))
                    .andExpect(content().contentType(INVOICE_CONTENT_FILE.getContentType()))
                    // https://www.rfc-editor.org/rfc/rfc9110.html#field.content-range; range-resp
//                    .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes ?-?/0")) // TODO: invalid value
                    .andExpect(content().bytes(new byte[0]));
        }
    }

    @Nested
    class HeadContent {

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
                    .andExpect(header().string(HttpHeaders.CONTENT_LENGTH,
                            String.valueOf(INVOICE_CONTENT_FILE.getBytes().length)));

            // for a HEAD request, nothing got read from the content store
            Mockito.verifyNoInteractions(contentStoreSpy);
        }

        @Test
        void head_empty_file_success() throws Exception {
            String invoiceId = createInvoice(new MockMultipartFile(
                    INVOICE_CONTENT_FILE.getName(),
                    INVOICE_CONTENT_FILE.getOriginalFilename(),
                    INVOICE_CONTENT_FILE.getContentType(),
                    InputStream.nullInputStream()
            ));

            mockMvc.perform(head("/invoices/{instanceId}/content", invoiceId))
                    .andExpect(status().isOk())
                    .andExpect(header().exists(HttpHeaders.ETAG))
                    .andExpect(content().contentType(INVOICE_CONTENT_FILE.getContentType()))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(INVOICE_CONTENT_FILE.getOriginalFilename(), StandardCharsets.UTF_8).build()
                            .toString()))
                    .andExpect(header().string(HttpHeaders.CONTENT_LENGTH,
                            String.valueOf(0)));
        }

        @ParameterizedTest
        @MethodSource("com.contentgrid.appserver.rest.entity.ContentRestControllerTest#nonExistentPaths")
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

        @Test
        void head_if_none_match_notModified() throws Exception {
            String invoiceId = createInvoice(INVOICE_CONTENT_FILE);

            var eTag = mockMvc.perform(head("/invoices/{id}/content", invoiceId))
                    .andExpect(status().isOk())
                    .andExpect(header().exists(HttpHeaders.ETAG))
                    .andReturn()
                    .getResponse()
                    .getHeader(HttpHeaders.ETAG);

            mockMvc.perform(head("/invoices/{id}/content", invoiceId)
                    .header(HttpHeaders.IF_NONE_MATCH, eTag)
            ).andExpect(status().isNotModified());
        }

        @Test
        void head_if_none_match_ok() throws Exception {
            String invoiceId = createInvoice(INVOICE_CONTENT_FILE);
            mockMvc.perform(head("/invoices/{id}/content", invoiceId)
                            .header(HttpHeaders.IF_NONE_MATCH, "\"xyz\"")
                    ).andExpect(status().isOk());
        }

        @Test
        void head_if_match_ok() throws Exception {
            String invoiceId = createInvoice(INVOICE_CONTENT_FILE);

            var eTag = mockMvc.perform(head("/invoices/{id}/content", invoiceId))
                    .andExpect(status().isOk())
                    .andExpect(header().exists(HttpHeaders.ETAG))
                    .andReturn()
                    .getResponse()
                    .getHeader(HttpHeaders.ETAG);

            mockMvc.perform(head("/invoices/{id}/content", invoiceId)
                            .header(HttpHeaders.IF_MATCH, eTag))
                    .andExpect(status().isOk());
        }

        @Test
        void head_if_match_precondition_failed() throws Exception {
            String invoiceId = createInvoice(INVOICE_CONTENT_FILE);
            mockMvc.perform(head("/invoices/{id}/content", invoiceId)
                            .header(HttpHeaders.IF_MATCH, "\"xyz\""))
                    .andExpect(status().isPreconditionFailed());
        }

    }

    @Nested
    class UploadContent {

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
        @MethodSource("com.contentgrid.appserver.rest.entity.ContentRestControllerTest#nonExistentPaths")
        void upload_nonexistent_fails(String pathTemplate, String problemType) throws Exception {
            String instanceId = createInvoice(null);

            mockMvc.perform(post(pathTemplate, instanceId)
                            .contentType(INVOICE_CONTENT_FILE.getContentType())
                            .content(INVOICE_CONTENT_FILE.getBytes()))
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                            .withStatusCode(HttpStatus.NOT_FOUND)
                            .withType(problemType)
                    );
        }

        @ParameterizedTest
        @MethodSource("com.contentgrid.appserver.rest.entity.ContentRestControllerTest#nonExistentPaths")
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
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                            .withStatusCode(HttpStatus.BAD_REQUEST)
                            .withType("https://contentgrid.cloud/problems/invalid-request/required-header")
                            .withTitle("Missing required header")
                            .withDetail("Required header 'Content-Type' is not present")
                            .withField("header", "Content-Type")
                    );

            Mockito.verifyNoInteractions(contentStoreSpy);

            mockMvc.perform(get("/invoices/{instanceId}/content", invoiceId))
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                            .withStatusCode(HttpStatus.NOT_FOUND)
                            .withType("https://contentgrid.cloud/problems/not-found/content"));
        }

        @Test
        void upload_empty_content_type_fails() throws Exception {
            String invoiceId = createInvoice(null);

            mockMvc.perform(post("/invoices/{instanceId}/content", invoiceId)
                            .contentType("")
                            .content(INVOICE_CONTENT_FILE.getBytes()))
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                            .withStatusCode(HttpStatus.BAD_REQUEST)
                            .withType("https://contentgrid.cloud/problems/invalid-request/required-header")
                            .withTitle("Missing required header")
                            .withDetail("Required header 'Content-Type' is not present")
                            .withField("header", "Content-Type")
                    );

            Mockito.verifyNoInteractions(contentStoreSpy);

            // No upload has happened
            mockMvc.perform(get("/invoices/{instanceId}/content", invoiceId))
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                            .withStatusCode(HttpStatus.NOT_FOUND)
                            .withType("https://contentgrid.cloud/problems/not-found/content"));

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
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                            .withStatusCode(HttpStatus.PRECONDITION_FAILED)
                            .withType("https://contentgrid.cloud/problems/unsatisfied-version")
                            .withTitle("Object has changed")
                            // details is different for If-Match/If-None-Match, so not asserted here
                            .withField("actual_version", v -> assertThat(v).asString().matches("[a-z0-9]+"))
                    );

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
        void upload_multipart_no_content_type_success_text_plain() throws Exception {
            String invoiceId = createInvoice(null);

            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    INVOICE_CONTENT_FILE.getOriginalFilename(),
                    null, // No content type
                    INVOICE_CONTENT_FILE.getBytes()
            );

            mockMvc.perform(multipart("/invoices/{instanceId}/content", invoiceId)
                            .file(file))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/invoices/{instanceId}/content", invoiceId))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("text/plain"));
        }


        @Test
        void upload_multipart_no_file_fails() throws Exception {
            String invoiceId = createInvoice(null);

            mockMvc.perform(multipart("/invoices/{instanceId}/content", invoiceId))
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                            .withStatusCode(HttpStatus.BAD_REQUEST)
                            .withDetail("Required part 'file' is not present.")
                    );

            Mockito.verifyNoInteractions(contentStoreSpy);

            mockMvc.perform(get("/invoices/{instanceId}/content", invoiceId))
                    .andExpect(status().isNotFound());
        }

        @ParameterizedTest
        @CsvSource({"PUT", "POST"})
        void upload_empty_file_success(HttpMethod method) throws Exception {
            String invoiceId = createInvoice(null);

            mockMvc.perform(request(method, "/invoices/{instanceId}/content", invoiceId)
                            .contentType(INVOICE_CONTENT_FILE.getContentType())
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename=\"" + INVOICE_CONTENT_FILE.getOriginalFilename() + "\"")
                            .content(new byte[0]))
                    .andExpect(status().isNoContent())
                    .andExpect(header().exists(HttpHeaders.ETAG));

            mockMvc.perform(get("/invoices/{instanceId}/content", invoiceId))
                    .andExpect(status().isOk())
                    .andExpect(header().exists(HttpHeaders.ETAG))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(INVOICE_CONTENT_FILE.getOriginalFilename(), StandardCharsets.UTF_8).build()
                            .toString()))
                    .andExpect(content().bytes(new byte[0]));
        }

        @ParameterizedTest
        @CsvSource({"PUT", "POST"})
        void upload_multipart_empty_file_success(HttpMethod method) throws Exception {
            String invoiceId = createInvoice(null);

            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    INVOICE_CONTENT_FILE.getOriginalFilename(),
                    INVOICE_CONTENT_FILE.getContentType(),
                    InputStream.nullInputStream()
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
                    .andExpect(content().bytes(new byte[0]));
        }

        /**
         * Reproducer: when the content store write fails with a CompletionException
         * (e.g. broken pipe to S3), the exception escapes uncaught through S3ContentStore,
         * ContentUploadAttributeMapper, and the controller. The response should be a server
         * error (5xx), but because the CompletionException bypasses all catch blocks, the
         * response status depends on unspecified error handling behavior.
         *
         * Additionally, the content must not be persisted in the database — a subsequent GET
         * should return 404.
         */
        @ParameterizedTest
        @CsvSource({"PUT", "POST"})
        void upload_contentStoreWriteFails_returnsServerError(HttpMethod method) throws Exception {
            String invoiceId = createInvoice(null);

            // Simulate S3 broken pipe: CompletionException wrapping IOException,
            // exactly as MinioAsyncClient.putObject().join() would throw
            Mockito.doThrow(new UnwritableContentException(ContentReference.UNKNOWN, new java.io.IOException("Broken pipe")))
                    .when(contentStoreSpy).writeContent(Mockito.any());

            mockMvc.perform(request(method, "/invoices/{instanceId}/content", invoiceId)
                            .contentType(INVOICE_CONTENT_FILE.getContentType())
                            .content(INVOICE_CONTENT_FILE.getBytes()))
                    .andExpect(status().is5xxServerError());

            Mockito.reset(contentStoreSpy);

            // Content must not have been persisted
            mockMvc.perform(get("/invoices/{instanceId}/content", invoiceId))
                    .andExpect(status().isNotFound());
        }

        /**
         * Same as above but with multipart upload, which is the exact path triggered
         * by the Python client in production.
         */
        @Test
        void upload_multipart_contentStoreWriteFails_returnsServerError() throws Exception {
            String invoiceId = createInvoice(null);

            Mockito.doThrow(new UnwritableContentException(ContentReference.UNKNOWN, new java.io.IOException("Broken pipe")))
                    .when(contentStoreSpy).writeContent(Mockito.any());

            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    INVOICE_CONTENT_FILE.getOriginalFilename(),
                    INVOICE_CONTENT_FILE.getContentType(),
                    INVOICE_CONTENT_FILE.getBytes()
            );

            mockMvc.perform(multipart("/invoices/{instanceId}/content", invoiceId)
                            .file(file))
                    .andExpect(status().is5xxServerError());

            Mockito.reset(contentStoreSpy);

            // Content must not have been persisted
            mockMvc.perform(get("/invoices/{instanceId}/content", invoiceId))
                    .andExpect(status().isNotFound());
        }

        @Test
        void upload_partial_put_not_supported() throws Exception {
            String invoiceId = createInvoice(INVOICE_CONTENT_FILE);

            mockMvc.perform(put("/invoices/{instanceId}/content", invoiceId)
                            .contentType(INVOICE_CONTENT_FILE.getContentType())
                            .header(HttpHeaders.CONTENT_RANGE, "bytes 0-3")
                            .content("updated")) // replace 'test' with 'updated'
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                            .withStatusCode(HttpStatus.BAD_REQUEST)
                            .withType("https://contentgrid.cloud/problems/invalid-request/forbidden-header")
                            .withTitle("Forbidden request header")
                            .withDetail("Request header 'Content-Range' is not allowed")
                            .withField("header", "Content-Range")
                    );

            Mockito.verifyNoInteractions(contentStoreSpy);
        }
    }

    @Nested
    class DeleteContent {

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

        @Test
        void delete_empty_file_success() throws Exception {
            String invoiceId = createInvoice(new MockMultipartFile(
                    INVOICE_CONTENT_FILE.getName(),
                    INVOICE_CONTENT_FILE.getOriginalFilename(),
                    INVOICE_CONTENT_FILE.getContentType(),
                    InputStream.nullInputStream()
            ));

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
        @MethodSource("com.contentgrid.appserver.rest.entity.ContentRestControllerTest#nonExistentPaths")
        void delete_nonexistent_fails(String pathTemplate, String problemType) throws Exception {
            String instanceId = createInvoice(null);

            mockMvc.perform(delete(pathTemplate, instanceId))
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                            .withStatusCode(HttpStatus.NOT_FOUND)
                            .withType(problemType)
                    );
        }

        @Test
        void delete_no_content_success() throws Exception {
            String invoiceId = createInvoice(null);

            // Try to delete content that doesn't exist
            mockMvc.perform(delete("/invoices/{instanceId}/content", invoiceId))
                    .andExpect(status().isNoContent()); // Should succeed even if content doesn't exist
        }
    }
}
