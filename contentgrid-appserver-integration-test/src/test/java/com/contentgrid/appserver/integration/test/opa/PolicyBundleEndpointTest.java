package com.contentgrid.appserver.integration.test.opa;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.integration.test.fixture.invoicing.InvoicingApiApplication;
import java.net.URI;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
                "contentgrid.events.rabbitmq.enabled=false",
                "management.endpoints.web.exposure.include=*",
                "management.server.port=0", // random, different port from the main port
        }
)
@ContextConfiguration(classes = InvoicingApiApplication.class)
@AutoConfigureRestTestClient
class PolicyBundleEndpointTest {

    private static final String BUNDLE_PATH = "/actuator/policybundle";

    @Autowired
    private RestTestClient rest;

    @LocalManagementPort
    private int managementPort;

    @Test
    void servesAGzippedBundleWithAnEtag() {
        var result = rest.get().uri(bundleUri())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Content-Type", "application/gzip")
                .returnResult();

        assertThat(result.getResponseHeaders().getFirst(HttpHeaders.ETAG))
                .asInstanceOf(InstanceOfAssertFactories.STRING)
                .startsWith("\"")
                .endsWith("\"");
    }

    @Test
    void repeatRequestWithTheEtagIsNotModified() {
        var etag = rest.get().uri(bundleUri()).exchange()
                .returnResult().getResponseHeaders().getFirst(HttpHeaders.ETAG);

        var result = rest.get().uri(bundleUri()).ifNoneMatch(etag)
                .exchange()
                .expectStatus().isEqualTo(304)
                .expectBody().isEmpty();

        assertThat(result.getResponseHeaders().getFirst(HttpHeaders.ETAG))
                .as("A 304 has to carry the entity tag it was matched against")
                .isEqualTo(etag);
    }

    @Test
    void requestWithStaleEtagReturnsBundle() {
        var etag = "\"stale-etag\"";

        var result = rest.get().uri(bundleUri()).ifNoneMatch(etag)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Content-Type", "application/gzip")
                .returnResult();

        assertThat(result.getResponseHeaders().getFirst(HttpHeaders.ETAG))
                .asInstanceOf(InstanceOfAssertFactories.STRING)
                .startsWith("\"")
                .endsWith("\"");
    }

    private URI bundleUri() {
        return URI.create("http://localhost:%d%s".formatted(managementPort, BUNDLE_PATH));
    }
}
