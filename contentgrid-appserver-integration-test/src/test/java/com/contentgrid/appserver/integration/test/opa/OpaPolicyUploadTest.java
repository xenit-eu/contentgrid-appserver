package com.contentgrid.appserver.integration.test.opa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.contentgrid.appserver.integration.test.fixture.invoicing.InvoicingApiApplication;
import com.contentgrid.appserver.security.opa.OpaPolicyUploadInitializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The {@code opa} readiness indicator has three distinct scenarios, each requiring its own Spring context since the
 * relevant properties (opa-sidecar vs. centralized mode, reachable vs. unreachable OPA) are fixed at startup:
 * <ul>
 *     <li>sidecar mode, OPA unreachable: upload never succeeds, readiness stays DOWN (this class)</li>
 *     <li>centralized mode: this app never uploads a policy, readiness is UP regardless of OPA's contents
 *     ({@link WhenOpaIsCentralized})</li>
 *     <li>sidecar mode, OPA reachable: upload succeeds, readiness becomes UP ({@link WhenSidecarUploadSucceeds})</li>
 * </ul>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "contentgrid.events.rabbitmq.enabled=false",
                "contentgrid.system.policyPackage=",
                "management.endpoints.web.exposure.include=*",
                "management.server.port=0",
                "management.endpoint.health.group.readiness.include=readinessState,opa",
                "opa.service.url=http://localhost:1",
        }
)
@AutoConfigureRestTestClient
@ContextConfiguration(classes = InvoicingApiApplication.class)
class OpaPolicyUploadTest {

    private static final Logger logger = LoggerFactory.getLogger(OpaPolicyUploadTest.class);

    private static final String POLICY_PATH = "rego/policy.rego";
    private static final String POLICY_ID = "appserver";
    private static final String POLICY_PACKAGE = "contentgrid.appserver";
    private static final String CENTRALIZED_POLICY_PACKAGE = "contentgrid.tenant.acme";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired(required = false)
    private OpaPolicyUploadInitializer opaPolicyUploadInitializer;

    @Autowired
    private RestTestClient rest;

    @Value("${local.management.port}")
    private int managementPort;

    @Test
    void readinessStaysDownWhenOpaIsUnreachable() {
        assertThat(opaPolicyUploadInitializer)
                .as("sidecar mode must create an upload initializer, without which no upload is ever attempted")
                .isNotNull();

        awaitReadiness("DOWN", 503);
    }

    @Nested
    @Testcontainers
    class WhenOpaIsCentralized {

        @Container
        static GenericContainer<?> opa = newOpaContainer();

        @DynamicPropertySource
        static void opaProperties(DynamicPropertyRegistry registry) {
            registry.add("contentgrid.system.policyPackage", () -> CENTRALIZED_POLICY_PACKAGE);
            registry.add("opa.service.url", () -> opaUrl(opa));
        }

        @Test
        void readinessIsUpWithoutUploadingAnyPolicy() throws Exception {
            awaitReadiness("UP", 200);

            assertThat(opaPolicyUploadInitializer)
                    .as("centralized mode must not create an upload initializer, since this app never owns the "
                            + "policy pushed to a centralized OPA")
                    .isNull();

            var response = queryOpaClient(opa, POLICY_ID);
            assertThat(response.statusCode())
                    .as("GET /v1/policies/%s should find nothing, since centralized mode never uploads", POLICY_ID)
                    .isEqualTo(404);
        }
    }

    @Nested
    @Testcontainers
    class WhenSidecarUploadSucceeds {

        @Container
        static GenericContainer<?> opa = newOpaContainer();

        @DynamicPropertySource
        static void opaProperties(DynamicPropertyRegistry registry) {
            registry.add("opa.service.url", () -> opaUrl(opa));
        }

        @Test
        void startupUploadsBlueprintArtifactPolicyToOpa() throws Exception {
            assertThat(opaPolicyUploadInitializer)
                    .as("OpaPolicyUploadInitializer bean, without which no upload is ever attempted")
                    .isNotNull();

            var expectedPolicy = expectedPolicy();
            // The upload retries indefinitely in the background rather than blocking startup, so poll instead of
            // asserting immediately.
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                var response = queryOpaClient(opa, POLICY_ID);
                assertThat(response.statusCode())
                        .as("GET /v1/policies/%s returned %d - %s", POLICY_ID, response.statusCode(),
                                response.body())
                        .isEqualTo(200);
                assertThat(OBJECT_MAPPER.readTree(response.body()).path("result").path("raw").asText())
                        .isEqualTo(expectedPolicy);
            });
            awaitReadiness("UP", 200);
        }
    }

    // Health-based readiness reflects a DOWN status as an HTTP 503, not 2xx (that's the point of a readiness probe).
    private void awaitReadiness(String expectedStatus, int expectedHttpStatus) {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                rest.get().uri("http://localhost:" + managementPort + "/actuator/health/readiness")
                        .exchange()
                        .expectStatus().isEqualTo(expectedHttpStatus)
                        .expectBody(String.class)
                        .value(body -> assertThat(body).contains("\"status\":\"" + expectedStatus + "\"")));
    }

    private static GenericContainer<?> newOpaContainer() {
        return new GenericContainer<>("openpolicyagent/opa:1.15.2-static")
                .withCommand("run", "--server", "--log-format=json-pretty", "--addr", "0.0.0.0:8181")
                .withExposedPorts(8181)
                .withLogConsumer(new Slf4jLogConsumer(logger))
                .waitingFor(Wait.forHttp("/health").forPort(8181));
    }

    private static String opaUrl(GenericContainer<?> opa) {
        return "http://%s:%d".formatted(opa.getHost(), opa.getMappedPort(8181));
    }

    private static String expectedPolicy() throws IOException {
        try (var stream = Objects.requireNonNull(
                OpaPolicyUploadTest.class.getClassLoader().getResourceAsStream(POLICY_PATH),
                POLICY_PATH + " is missing from the test classpath")) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("${system.policy.package}", POLICY_PACKAGE);
        }
    }

    private static HttpResponse<String> queryOpaClient(GenericContainer<?> opa, String policyId)
            throws IOException, InterruptedException {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create("http://%s:%d/v1/policies/%s"
                            .formatted(opa.getHost(), opa.getMappedPort(8181), policyId)))
                    .GET()
                    .build();
            return client.send(request, BodyHandlers.ofString());
        }
    }
}
