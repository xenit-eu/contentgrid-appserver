package com.contentgrid.appserver.example;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.endpoints.web.exposure.include=*",
                "management.prometheus.metrics.export.enabled=true", //bootRun profile disables metrics, which is fine for other tests but should be overridden here
                "management.server.port=0" // random, different port from main port
        }
)
@ActiveProfiles("bootRun")
public class ContentgridPrometheusActuatorTest {
    @Autowired
    private TestRestTemplate rest;

    @Value("${local.management.port}")
    int managementPort;

    @Test
    void prometheusEndpointIsPublic() {
        ResponseEntity<String> resp = rest.getForEntity("http://localhost:" + managementPort + "/actuator/prometheus", String.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
