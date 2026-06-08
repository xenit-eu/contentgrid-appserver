package com.contentgrid.appserver.example;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.endpoints.web.exposure.include=*",
                "management.prometheus.metrics.export.enabled=true", //bootRun profile disables metrics, which is fine for other tests but should be overridden here
                "management.server.port=0" // random, different port from main port
        }
)
@ActiveProfiles("bootRun")
class ContentgridPrometheusActuatorTest {

    @Value("${local.management.port}")
    int managementPort;

    private WebTestClient getClient() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + managementPort).build();
    }

    @Test
    void prometheusEndpointIsPublic() {
        ResponseSpec resp = getClient().get().uri("/actuator/prometheus").exchange();
        assertThat(resp.expectStatus().is2xxSuccessful());
    }
}
