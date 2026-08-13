package com.contentgrid.appserver.integration.test.opa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.contentgrid.appserver.integration.test.fixture.invoicing.InvoicingApiApplication;
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

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "contentgrid.events.rabbitmq.enabled=false",
                "management.endpoints.web.exposure.include=*",
                "management.endpoint.health.group.readiness.include=readinessState,opa",
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

    @Autowired
    private RestTestClient rest;

    @Value("${local.management.port}")
    private int managementPort;

    @Nested
    @Testcontainers
    class WhenOpaIsCentralized {

        @Container
        static GenericContainer<?> opa = newOpaContainer();

        @DynamicPropertySource
        static void opaProperties(DynamicPropertyRegistry registry) {
            registry.add("contentgrid.system.policyPackage", () -> CENTRALIZED_POLICY_PACKAGE);
            registry.add("management.endpoint.health.group.readiness.include", () -> "readinessState");
        }

        @Test
        void readinessIsUpWithoutUploadingAnyPolicy() throws Exception {
            awaitUp();

            var response = queryOpaClient(opa);
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
            awaitUp();
            var expectedPolicy = expectedPolicy();
            var response = queryOpaClient(opa);
            assertThat(response.statusCode())
                    .as("GET /v1/policies/%s should find nothing, since centralized mode never uploads", POLICY_ID)
                    .isEqualTo(200);
            assertThat(OBJECT_MAPPER.readTree(response.body()).path("result").path("raw").asText())
                    .isEqualTo(expectedPolicy);
        }
    }

    private void awaitUp() {
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                rest.get().uri("http://localhost:" + managementPort + "/actuator/health/readiness")
                        .exchange()
                        .expectStatus().isEqualTo(200)
                        .expectBody(String.class)
                        .value(body -> assertThat(body).contains("\"status\":\"" + "UP" + "\"")));
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

    private static HttpResponse<String> queryOpaClient(GenericContainer<?> opa)
            throws IOException, InterruptedException {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create("http://%s:%d/v1/policies/%s"
                            .formatted(opa.getHost(), opa.getMappedPort(8181), OpaPolicyUploadTest.POLICY_ID)))
                    .GET()
                    .build();
            return client.send(request, BodyHandlers.ofString());
        }
    }
}
