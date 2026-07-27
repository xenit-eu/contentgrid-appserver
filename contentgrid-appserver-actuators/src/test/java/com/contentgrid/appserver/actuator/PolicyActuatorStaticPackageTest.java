package com.contentgrid.appserver.actuator;

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
                "contentgrid.system.deploymentId=fb0e9318-f389-4300-a64e-dba3532e6ac0",
                "contentgrid.system.applicationId=336d61a5-94cd-4b7a-b90b-369fbe2ef78c",
                "contentgrid.system.policyPackage=",
                "management.endpoints.web.exposure.include=*",
                "management.server.port=0"
        }
)
@AutoConfigureRestTestClient
class PolicyActuatorStaticPackageTest {

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
            return new ClassPathBlueprintArtifact(
                    PolicyActuatorStaticPackageTest.class.getClassLoader(),
                    Path.of("blueprint-artifact-static"));
        }
    }

    @Test
    void actuatorJsonDoesNotContainLinkToPolicyWhenPolicyPackageEmpty() {
        rest.get().uri("http://localhost:" + managementPort + "/actuator")
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody()
                .jsonPath("$._links.policy").doesNotExist();
    }

    @Test
    void policyEndpointReturns404WhenPolicyPackageEmpty() {
        rest.get().uri("http://localhost:" + managementPort + "/actuator/policy")
                .exchange()
                .expectStatus().isNotFound();
    }
}
