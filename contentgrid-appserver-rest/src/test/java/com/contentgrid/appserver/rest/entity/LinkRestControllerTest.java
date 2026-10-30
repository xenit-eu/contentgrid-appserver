package com.contentgrid.appserver.rest.entity;

import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.APPLICATION;
import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.PERSON;
import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.PERSON_PORTRAIT_LINK;
import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.PERSON_SUMMARY_LINK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.contentstore.impl.utils.testing.MockContentStore;
import com.contentgrid.appserver.domain.ContentApi.Content;
import com.contentgrid.appserver.domain.DatamodelApi;
import com.contentgrid.appserver.domain.StoredLinkValue;
import com.contentgrid.appserver.domain.StoredLinkValue.ContentValue;
import com.contentgrid.appserver.domain.StoredLinkValue.TextValue;
import com.contentgrid.appserver.domain.authorization.AuthorizationContext;
import com.contentgrid.appserver.domain.content.ContentStoreResolver;
import com.contentgrid.appserver.domain.data.DataEntry.FileDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.StringDataEntry;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.LinkRequest;
import com.contentgrid.appserver.query.engine.api.TableCreator;
import com.contentgrid.appserver.rest.entity.EntityRestControllerTest.TestConfig;
import com.contentgrid.appserver.rest.test.ProblemDetailsMockMvcMatchers;
import com.contentgrid.appserver.rest.test.TestApplication;
import com.contentgrid.appserver.rest.test.WithMockJwt;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(classes = {TestApplication.class, TestConfig.class}, properties = {
        "spring.servlet.encoding.enabled=false", // disables mock-mvc enforcing charset in request
})
@AutoConfigureMockMvc
@WithMockJwt
public class LinkRestControllerTest {

    private static final String PORTRAIT_LINK_PATH = "/_links/16-9-hash123/portrait";
    private static final String SUMMARY_LINK_PATH = "/_links/summary-hash123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private DatamodelApi datamodelApi;

    @MockitoBean
    private ContentStoreResolver contentStoreResolverMock;

    @AutoClose
    private final ContentStore contentStore = new MockContentStore();

    @TestConfiguration
    static class TestConfig {

        @Bean
        Clock fixedClock() {
            return Clock.fixed(Instant.ofEpochSecond(1234567890), ZoneOffset.UTC);
        }
    }

    @Autowired
    TableCreator tableCreator;

    @BeforeEach
    void setup() {
        tableCreator.createTables(APPLICATION);
        Mockito.when(contentStoreResolverMock.resolve(Mockito.any())).thenReturn(contentStore);
    }

    @AfterEach
    void tearDown() {
        tableCreator.dropTables(APPLICATION);
    }

    private String createPerson() throws Exception {
        return mockMvc.perform(post("/persons")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "test")
                        .param("vat", UUID.randomUUID().toString())
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.LOCATION);
    }

    private static EntityId personId(String personUrl) {
        return EntityId.of(UUID.fromString(personUrl.substring(personUrl.lastIndexOf('/') + 1)));
    }

    private LinkRequest portraitLinkRequest(String personUrl) {
        return LinkRequest.forLink(PERSON.getName(), personId(personUrl), PERSON_PORTRAIT_LINK.getIdentity());
    }

    private LinkRequest summaryLinkRequest(String personUrl) {
        return LinkRequest.forLink(PERSON.getName(), personId(personUrl), PERSON_SUMMARY_LINK.getIdentity());
    }

    private StoredLinkValue seedSummary(String personUrl) {
        return datamodelApi.updateLink(
                APPLICATION,
                summaryLinkRequest(personUrl),
                new StringDataEntry("person summary"),
                AuthorizationContext.allowAll()
        );
    }

    private Optional<StoredLinkValue> findSummary(String personUrl) {
        return datamodelApi.findLink(APPLICATION, summaryLinkRequest(personUrl), AuthorizationContext.allowAll());
    }

    private StoredLinkValue seedPortrait(String personUrl) {
        return datamodelApi.updateLink(
                APPLICATION,
                portraitLinkRequest(personUrl),
                new FileDataEntry("portrait.png", "image/png",
                        () -> new ByteArrayInputStream("portrait data".getBytes(StandardCharsets.UTF_8))),
                AuthorizationContext.allowAll()
        );
    }

    private Content findPortrait(String personUrl) {
        return datamodelApi.findLink(APPLICATION, portraitLinkRequest(personUrl), AuthorizationContext.allowAll())
                .map(ContentValue.class::cast)
                .map(ContentValue::getContent)
                .orElseThrow();
    }

    @Nested
    class GetLink {

        @Test
        void testSuccessfullyGetLink() throws Exception {
            var personUrl = createPerson();
            seedPortrait(personUrl);

            mockMvc.perform(get(personUrl + PORTRAIT_LINK_PATH))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                    .andExpect(header().exists(HttpHeaders.ETAG))
                    .andExpect(content().string("portrait data"));
        }

        @Test
        void testSuccessfullyGetTextLink() throws Exception {
            var personUrl = createPerson();
            seedSummary(personUrl);

            mockMvc.perform(get(personUrl + SUMMARY_LINK_PATH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.value").value("person summary"));
        }

        @Test
        void testGetLinkWithoutContent() throws Exception {
            var personUrl = createPerson();

            mockMvc.perform(get(personUrl + PORTRAIT_LINK_PATH))
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                            .withStatusCode(HttpStatus.NOT_FOUND)
                            .withType("https://contentgrid.cloud/problems/not-found/content")
                            .withTitle("Content not found")
                    );
        }

        @Test
        void testGetLink_ifNoneMatchUnmodified() throws Exception {
            var personUrl = createPerson();
            seedPortrait(personUrl);

            var etag = mockMvc.perform(get(personUrl + PORTRAIT_LINK_PATH))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getHeader(HttpHeaders.ETAG);

            mockMvc.perform(get(personUrl + PORTRAIT_LINK_PATH)
                            .header(HttpHeaders.IF_NONE_MATCH, etag))
                    .andExpect(status().isNotModified());
        }

        @Test
        void testGetLinkWithIncorrectIfMatch() throws Exception {
            var personUrl = createPerson();
            seedPortrait(personUrl);

            mockMvc.perform(get(personUrl + PORTRAIT_LINK_PATH)
                            .header(HttpHeaders.IF_MATCH, "\"some-other-etag\""))
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                            .withStatusCode(HttpStatus.PRECONDITION_FAILED)
                            .withType("https://contentgrid.cloud/problems/unsatisfied-version")
                            .withTitle("Object has changed")
                    );
        }

        @Test
        void testGetNonExistingLink() throws Exception {
            var personUrl = createPerson();

            mockMvc.perform(get(personUrl + "/_links/does-not-exist/portrait"))
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                            .withStatusCode(HttpStatus.NOT_FOUND)
                            .withType("https://contentgrid.cloud/problems/not-found/endpoint")
                            .withTitle("Endpoint not found")
                    );
        }

        @Test
        void testGetLinkOnNonExistingEntity() throws Exception {
            mockMvc.perform(get("/persons/" + UUID.randomUUID() + PORTRAIT_LINK_PATH))
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                            .withStatusCode(HttpStatus.NOT_FOUND)
                            .withType("https://contentgrid.cloud/problems/not-found/entity-item")
                            .withTitle("Entity item not found")
                    );
        }
    }

    @Nested
    class UpdateLink {

        @Test
        void testSuccessfullyUpdateContentLink() throws Exception {
            var personUrl = createPerson();

            mockMvc.perform(put(personUrl + PORTRAIT_LINK_PATH)
                            .contentType(MediaType.IMAGE_JPEG)
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"new.jpg\"")
                            .content("new portrait data".getBytes(StandardCharsets.UTF_8)))
                    .andExpect(status().isNoContent())
                    .andExpect(header().exists(HttpHeaders.ETAG));

            // Verify link content was updated
            var portrait = findPortrait(personUrl);
            assertThat(portrait.getFilename()).isEqualTo("new.jpg");
            assertThat(portrait.getMimeType()).isEqualTo(MediaType.IMAGE_JPEG_VALUE);
            assertThat(portrait.getInputStream()).hasContent("new portrait data");
        }

        @Test
        void testSuccessfullyUpdateTextLink() throws Exception {
            var personUrl = createPerson();

            mockMvc.perform(put(personUrl + SUMMARY_LINK_PATH)
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("updated summary"))
                    .andExpect(status().isNoContent())
                    .andExpect(header().exists(HttpHeaders.ETAG));

            // Verify link value was updated
            assertThat(findSummary(personUrl))
                    .get()
                    .isInstanceOfSatisfying(TextValue.class,
                            textValue -> assertThat(textValue.getValue()).isEqualTo("updated summary"));
        }

        @Test
        void testUpdateTextLinkWithIfMatch() throws Exception {
            var personUrl = createPerson();

            var etag = mockMvc.perform(put(personUrl + SUMMARY_LINK_PATH)
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("first summary"))
                    .andExpect(status().isNoContent())
                    .andReturn()
                    .getResponse()
                    .getHeader(HttpHeaders.ETAG);

            // The version changes along with the stored text
            var newEtag = mockMvc.perform(put(personUrl + SUMMARY_LINK_PATH)
                            .header(HttpHeaders.IF_MATCH, etag)
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("second summary"))
                    .andExpect(status().isNoContent())
                    .andReturn()
                    .getResponse()
                    .getHeader(HttpHeaders.ETAG);
            assertThat(newEtag).isNotEqualTo(etag);

            // The old version no longer matches
            mockMvc.perform(put(personUrl + SUMMARY_LINK_PATH)
                            .header(HttpHeaders.IF_MATCH, etag)
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("third summary"))
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                            .withStatusCode(HttpStatus.PRECONDITION_FAILED)
                            .withType("https://contentgrid.cloud/problems/unsatisfied-version")
                            .withTitle("Object has changed")
                    );

            assertThat(findSummary(personUrl))
                    .get()
                    .isInstanceOfSatisfying(TextValue.class,
                            textValue -> assertThat(textValue.getValue()).isEqualTo("second summary"));
        }

        @Test
        void testUpdateLinkWithCorrectIfMatch() throws Exception {
            var personUrl = createPerson();
            seedPortrait(personUrl);

            var etag = mockMvc.perform(get(personUrl + PORTRAIT_LINK_PATH))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getHeader(HttpHeaders.ETAG);

            mockMvc.perform(put(personUrl + PORTRAIT_LINK_PATH)
                            .header(HttpHeaders.IF_MATCH, etag)
                            .contentType(MediaType.IMAGE_JPEG)
                            .content("new portrait data".getBytes(StandardCharsets.UTF_8)))
                    .andExpect(status().isNoContent());

            // Verify link content was updated
            assertThat(findPortrait(personUrl).getMimeType()).isEqualTo(MediaType.IMAGE_JPEG_VALUE);
        }

        @Test
        void testUpdateLinkWithIncorrectIfMatch() throws Exception {
            var personUrl = createPerson();
            seedPortrait(personUrl);

            mockMvc.perform(put(personUrl + PORTRAIT_LINK_PATH)
                            .header(HttpHeaders.IF_MATCH, "\"some-other-etag\"")
                            .contentType(MediaType.IMAGE_JPEG)
                            .content("new portrait data".getBytes(StandardCharsets.UTF_8)))
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                            .withStatusCode(HttpStatus.PRECONDITION_FAILED)
                            .withType("https://contentgrid.cloud/problems/unsatisfied-version")
                            .withTitle("Object has changed")
                    );

            // Verify link content was not changed
            var portrait = findPortrait(personUrl);
            assertThat(portrait.getFilename()).isEqualTo("portrait.png");
            assertThat(portrait.getMimeType()).isEqualTo("image/png");
        }

        @Test
        void testUpdateTextLinkWithUnsupportedContentType() throws Exception {
            var personUrl = createPerson();

            mockMvc.perform(put(personUrl + SUMMARY_LINK_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("\"updated summary\""))
                    .andExpect(status().isUnsupportedMediaType());

            // Verify link value was not set
            assertThat(findSummary(personUrl)).isEmpty();
        }

        @Test
        void testUpdateNonExistingLink() throws Exception {
            var personUrl = createPerson();

            mockMvc.perform(put(personUrl + "/_links/does-not-exist/portrait")
                            .contentType(MediaType.IMAGE_JPEG)
                            .content("new portrait data".getBytes(StandardCharsets.UTF_8)))
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                            .withStatusCode(HttpStatus.NOT_FOUND)
                            .withType("https://contentgrid.cloud/problems/not-found/endpoint")
                            .withTitle("Endpoint not found")
                    );
        }

        @Test
        void testUpdateLinkOnNonExistingEntity() throws Exception {
            mockMvc.perform(put("/persons/" + UUID.randomUUID() + PORTRAIT_LINK_PATH)
                            .contentType(MediaType.IMAGE_JPEG)
                            .content("new portrait data".getBytes(StandardCharsets.UTF_8)))
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                            .withStatusCode(HttpStatus.NOT_FOUND)
                            .withType("https://contentgrid.cloud/problems/not-found/entity-item")
                            .withTitle("Entity item not found")
                    );
        }
    }

    @Nested
    class DeleteLink {

        @Test
        void testSuccessfullyDeleteLink() throws Exception {
            var personUrl = createPerson();
            seedPortrait(personUrl);

            mockMvc.perform(delete(personUrl + PORTRAIT_LINK_PATH))
                    .andExpect(status().isNoContent());

            // Verify link content was cleared
            var portrait = findPortrait(personUrl);
            assertThat(portrait.getFilename()).isNull();
            assertThat(portrait.getMimeType()).isNull();
        }

        @Test
        void testSuccessfullyDeleteTextLink() throws Exception {
            var personUrl = createPerson();
            seedSummary(personUrl);

            mockMvc.perform(delete(personUrl + SUMMARY_LINK_PATH))
                    .andExpect(status().isNoContent());

            // Verify link value was cleared
            assertThat(findSummary(personUrl)).isEmpty();
        }

        @Test
        void testDeleteNonExistingLink() throws Exception {
            var personUrl = createPerson();

            mockMvc.perform(delete(personUrl + "/_links/does-not-exist/portrait"))
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                            .withStatusCode(HttpStatus.NOT_FOUND)
                            .withType("https://contentgrid.cloud/problems/not-found/endpoint")
                            .withTitle("Endpoint not found")
                    );
        }

        @Test
        void testDeleteLinkWithIncorrectIfMatch() throws Exception {
            var personUrl = createPerson();
            seedPortrait(personUrl);

            mockMvc.perform(delete(personUrl + PORTRAIT_LINK_PATH)
                            .header(HttpHeaders.IF_MATCH, "\"some-other-etag\""))
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                            .withStatusCode(HttpStatus.PRECONDITION_FAILED)
                            .withType("https://contentgrid.cloud/problems/unsatisfied-version")
                            .withTitle("Object has changed")
                    );

            // Verify link content still exists
            var portrait = findPortrait(personUrl);
            assertThat(portrait.getFilename()).isEqualTo("portrait.png");
            assertThat(portrait.getMimeType()).isEqualTo("image/png");
        }

        @Test
        void testDeleteLinkOnNonExistingEntity() throws Exception {
            mockMvc.perform(delete("/persons/" + UUID.randomUUID() + PORTRAIT_LINK_PATH))
                    .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                            .withStatusCode(HttpStatus.NOT_FOUND)
                            .withType("https://contentgrid.cloud/problems/not-found/entity-item")
                            .withTitle("Entity item not found")
                    );
        }
    }

}