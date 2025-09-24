package com.contentgrid.appserver.actuator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "contentgrid.system.policyPackage=xfb0e9318f3894300a64edba3532e6ac0",
                "management.endpoints.web.exposure.include=*",
                "management.server.port=0" // random, different port from main port
        }
)
public class ContentgridActuatorConfigurationTest {
    @Autowired
    private TestRestTemplate rest;

    @Value("${local.management.port}")
    int managementPort;

    @SpringBootApplication
    static class TestApplication {
        public static void main(String[] args) {
            SpringApplication.run(TestApplication.class, args);
        }
    }

    @Test
    void contextLoads() {}

    @Test
    void healthEndpointIsPublic() {
        ResponseEntity<String> resp = rest.getForEntity("http://localhost:" + managementPort + "/actuator/health", String.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void policyEndpointIsPublic() {
        System.out.println(managementPort);
        ResponseEntity<String> resp = rest.getForEntity("http://localhost:" + managementPort + "/actuator/policy", String.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getHeaders().getContentType().toString())
                .isEqualTo("application/vnd.cncf.openpolicyagent.policy.layer.v1+rego;charset=UTF-8");
        assertThat(resp.getBody()).contains("xfb0"); // templating works
    }
}
