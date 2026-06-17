package com.contentgrid.appserver.actuator;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.blueprintartifact.impl.fs.classpath.ClassPathBlueprintArtifact;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;

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
@AutoConfigureRestTestClient
public class ActuatorConfigurationTest {
    @Autowired
    private RestTestClient rest;

    @Value("${local.management.port}")
    int managementPort;

    @SpringBootApplication
    static class TestApplication {
        public static void main(String[] args) {
            SpringApplication.run(TestApplication.class, args);
        }

        @Bean
        BlueprintArtifact testBlueprintArtifact() {
            return new ClassPathBlueprintArtifact(ActuatorConfigurationTest.class.getClassLoader(), Path.of("blueprint-artifact"));
        }
    }

    @Test
    void contextLoads() {}

    @Test
    void healthEndpointIsPublic() {
        rest.get().uri("http://localhost:" + managementPort + "/actuator/health")
                .exchange()
                .expectStatus().is2xxSuccessful();
    }

    @Test
    void policyEndpointIsPublic() {
        rest.get().uri("http://localhost:" + managementPort + "/actuator/policy")
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectHeader().valueEquals("Content-Type",
                        "application/vnd.cncf.openpolicyagent.policy.layer.v1+rego;charset=UTF-8")
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("xfb0")); // templating works
    }

    @Test
    void webhooksEndpointIsPublic() {
        rest.get().uri("http://localhost:" + managementPort + "/actuator/webhooks")
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectHeader().valueEquals("Content-Type", "application/vnd.contentgrid.webhooks.v1+json")
                // Check application id/deployment id templating
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("18-f3", "a5-94"));
    }
}
