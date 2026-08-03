package com.contentgrid.appserver.integration.test.opa;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.integration.test.fixture.invoicing.InvoicingApiApplication;
import com.contentgrid.appserver.security.opa.OpaPolicyUploader;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {
        "contentgrid.events.rabbitmq.enabled=false",
        "contentgrid.system.policyPackage=",
})
@ContextConfiguration(classes = InvoicingApiApplication.class)
class OpaPolicyUploadTest {

    private static final Logger logger = LoggerFactory.getLogger(OpaPolicyUploadTest.class);

    private static final String POLICY_PATH = "rego/policy.rego";
    private static final String POLICY_ID = "appserver";
    private static final String POLICY_PACKAGE = "contentgrid.appserver";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Container
    static GenericContainer<?> opa = new GenericContainer<>("openpolicyagent/opa:1.15.2-static")
            .withCommand("run", "--server", "--log-format=json-pretty", "--addr", "0.0.0.0:8181")
            .withExposedPorts(8181)
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .waitingFor(Wait.forHttp("/health").forPort(8181));

    @DynamicPropertySource
    static void opaProperties(DynamicPropertyRegistry registry) {
        registry.add("opa.service.url",
                () -> "http://%s:%d".formatted(opa.getHost(), opa.getMappedPort(8181)));
    }

    @Autowired(required = false)
    private OpaPolicyUploader opaPolicyUploader;

    @Test
    void startupUploadsBlueprintArtifactPolicyToOpa() throws Exception {
        assertThat(opaPolicyUploader)
                .as("OpaPolicyUploader bean, without which no upload is ever attempted")
                .isNotNull();

        assertThat(fetchPolicyFromOpa(POLICY_ID)).isEqualTo(expectedPolicy());
    }

    /**
     * The policy as it sits on disk, with the same placeholder substitution {@link OpaPolicyUploader} applies.
     */
    private static String expectedPolicy() throws IOException {
        try (var stream = Objects.requireNonNull(
                OpaPolicyUploadTest.class.getClassLoader().getResourceAsStream(POLICY_PATH),
                POLICY_PATH + " is missing from the test classpath")) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("${system.policy.package}", POLICY_PACKAGE);
        }
    }

    /**
     * Reads back the raw (unparsed) module OPA stores under the given policy id.
     */
    private static String fetchPolicyFromOpa(String policyId) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create("http://%s:%d/v1/policies/%s"
                            .formatted(opa.getHost(), opa.getMappedPort(8181), policyId)))
                    .GET()
                    .build();
            var response = client.send(request, BodyHandlers.ofString());
            assertThat(response.statusCode())
                    .as("GET /v1/policies/%s returned %d - %s", policyId, response.statusCode(), response.body())
                    .isEqualTo(200);
            return OBJECT_MAPPER.readTree(response.body()).path("result").path("raw").asText();
        }
    }
}
