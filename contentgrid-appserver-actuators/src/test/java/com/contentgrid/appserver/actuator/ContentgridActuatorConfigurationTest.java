package com.contentgrid.appserver.actuator;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.infrastructure.api.Artifact;
import com.contentgrid.appserver.infrastructure.impl.fs.classpath.ClassPathArtifact;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "contentgrid.system.policyPackage=xfb0e9318f3894300a64edba3532e6ac0",
                "contentgrid.system.deploymentId=fb0e9318-f389-4300-a64e-dba3532e6ac0",
                "contentgrid.system.applicationId=336d61a5-94cd-4b7a-b90b-369fbe2ef78c",
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

        @Bean
        Artifact TestArtifact() {
            return new ClassPathArtifact(ContentgridActuatorConfigurationTest.class.getClassLoader(), Path.of(""));
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
        ResponseEntity<String> resp = rest.getForEntity("http://localhost:" + managementPort + "/actuator/policy", String.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getHeaders().getContentType())
                .hasToString("application/vnd.cncf.openpolicyagent.policy.layer.v1+rego;charset=UTF-8");
        assertThat(resp.getBody()).contains("xfb0"); // templating works
    }

    @Test
    void webhooksEndpointIsPublic() {
        ResponseEntity<String> resp = rest.getForEntity("http://localhost:" + managementPort + "/actuator/webhooks", String.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getHeaders().getContentType())
                .hasToString("application/vnd.contentgrid.webhooks.v1+json");
        // Check application id/deployment id templating
        assertThat(resp.getBody()).contains("18-f3");
        assertThat(resp.getBody()).contains("a5-94");
    }
}
