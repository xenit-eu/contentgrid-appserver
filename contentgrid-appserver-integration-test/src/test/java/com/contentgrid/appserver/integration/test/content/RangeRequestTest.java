package com.contentgrid.appserver.integration.test.content;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.registry.ApplicationResolver;
import com.contentgrid.appserver.registry.SingleApplicationResolver;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
                "contentgrid.security.unauthenticated.allow = true",
                "contentgrid.security.csrf.disabled = true",
                "contentgrid.appserver.content-store.type = ephemeral",
                "contentgrid.thunx.abac.source = none",
                "contentgrid.events.rabbitmq.enabled=false",
                "spring.datasource.url=jdbc:tc:postgresql:15:///",
        })
class RangeRequestTest {

    private static final Application APPLICATION = Application.builder()
            .name(ApplicationName.of("default"))
            .entity(Entity.builder()
                    .name(EntityName.of("person"))
                    .table(TableName.of("person"))
                    .pathSegment(PathSegmentName.of("persons"))
                    .linkName(LinkName.of("person"))
                    .attribute(SimpleAttribute.builder()
                            .name(AttributeName.of("name"))
                            .column(ColumnName.of("name"))
                            .type(Type.TEXT)
                            .build())
                    .attribute(ContentAttribute.builder()
                            .name(AttributeName.of("file"))
                            .pathSegment(PathSegmentName.of("file"))
                            .linkName(LinkName.of("file"))
                            .idColumn(ColumnName.of("file__id"))
                            .filenameColumn(ColumnName.of("file__filename"))
                            .mimetypeColumn(ColumnName.of("file__mimetype"))
                            .lengthColumn(ColumnName.of("file__length"))
                            .build())
                    .build())
            .build();

    private static final String TEXT = "Hello world!";
    private static final byte[] CONTENT = TEXT.getBytes(StandardCharsets.UTF_8);
    private static final String FILENAME = "hello.txt";

    private String contentUrl;

    @LocalServerPort
    private int port;

    private WebTestClient client;

    @BeforeEach
    void setup() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:%s".formatted(port))
                .responseTimeout(Duration.ofMinutes(10)) // For debugging
                .build();

        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
        multipartBodyBuilder.part("file", new ByteArrayResource(CONTENT), MediaType.TEXT_PLAIN)
                .filename(FILENAME);
        multipartBodyBuilder.part("name", "test");

        // Upload content
        var response = client.post()
                .uri("/persons", port)
                .body(BodyInserters.fromMultipartData(multipartBodyBuilder.build()))
                .exchange()
                .expectStatus().isCreated()
                .returnResult(Void.class);

        contentUrl = response.getResponseHeaders().getLocation() + "/file";
    }

    @Test
    void rangeRequest_http206() {
        var start = 5;
        var end = 9;

        var expected = Arrays.copyOfRange(CONTENT, start, end + 1);

        client.get().uri(contentUrl)
                .accept(MediaType.ALL)
                .header(HttpHeaders.RANGE, "bytes=%s-%s".formatted(start, end))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.PARTIAL_CONTENT)
                .expectBody(byte[].class).isEqualTo(expected);
    }

    @Test
    void rangeRequest_upToLastByte_http206() {
        var start = 5;
        var end = CONTENT.length;
        var expected = Arrays.copyOfRange(CONTENT, start, end);

        client.get().uri(contentUrl)
                .accept(MediaType.ALL)
                .header(HttpHeaders.RANGE, "bytes=%s-".formatted(start))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.PARTIAL_CONTENT)
                .expectBody(byte[].class).isEqualTo(expected);
    }

    @Test
    void rangeRequest_lastNBytes_http206() {
        var length = 5;
        var end = CONTENT.length;
        var expected = Arrays.copyOfRange(CONTENT, end - length, end);

        client.get().uri(contentUrl)
                .accept(MediaType.ALL)
                .header(HttpHeaders.RANGE, "bytes=-%s".formatted(length))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.PARTIAL_CONTENT)
                .expectBody(byte[].class).isEqualTo(expected);
    }

    @Test
    void unsatisfiableRangeRequest_http416() {
        var start = 50;
        var end = 54;
        client.get().uri(contentUrl)
                .accept(MediaType.ALL)
                .header(HttpHeaders.RANGE, "bytes=%s-%s".formatted(start, end))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
    }

    @ParameterizedTest
    @CsvSource({
            "10,9",  // start > end
            "-1,9",  // start < 0
    })
    void invalidRangeRequest_http400(int start, int end) {
        client.get().uri(contentUrl)
                .accept(MediaType.ALL)
                .header(HttpHeaders.RANGE, "bytes=%s-%s".formatted(start, end))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @SpringBootApplication
    static class TestApp {

        @Bean
        ApplicationResolver applicationResolver() {
            return new SingleApplicationResolver(APPLICATION);
        }

    }
}
