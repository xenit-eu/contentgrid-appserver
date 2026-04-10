package com.contentgrid.appserver.actuator;

import com.contentgrid.appserver.actuator.policy.PolicyActuator;
import com.contentgrid.appserver.actuator.policy.PolicyVariables;
import com.contentgrid.appserver.actuator.webhooks.WebhookConfigActuator;
import com.contentgrid.appserver.actuator.webhooks.WebhookVariables;
import com.contentgrid.appserver.infrastructure.api.Artifact;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntry;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryNotFoundException;
import com.contentgrid.common.spring.actuators.ExposedActuatorEndpoint;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ActuatorConfiguration {

    @Bean
    PolicyVariables policyVariables(ContentgridApplicationProperties applicationProperties) {
        return PolicyVariables.builder()
                .policyPackageName(applicationProperties.getSystem().getPolicyPackage())
                .build();
    }

    @Bean
    @SneakyThrows
    PolicyActuator policyActuator(PolicyVariables policyVariables, Artifact artifact) {
        ArtifactEntry entry;
        try {
            entry = artifact.load(Path.of("rego", "policy.rego"));
        } catch (ArtifactEntryNotFoundException e) {
            entry = null; // not found
        }
        return new PolicyActuator(entry, policyVariables);
    }

    @Bean
    ExposedActuatorEndpoint exposedPolicyActuatorEndpoint() {
        return new ExposedActuatorEndpoint(PolicyActuator.class);
    }

    @Bean
    WebhookVariables webhookVariables(ContentgridApplicationProperties applicationProperties) {
        return WebhookVariables.builder()
                .systemProperties(applicationProperties.getSystem())
                .userVariables(applicationProperties.getVariables())
                .build();
    }

    @Bean
    @SneakyThrows
    WebhookConfigActuator webHooksConfigActuator(WebhookVariables webhookVariables, Artifact artifact) {
        ArtifactEntry entry;
        try {
            entry = artifact.load(Path.of("eventhandler", "webhooks.json"));
        } catch (ArtifactEntryNotFoundException e) {
            entry = null; // not found
        }
        return new WebhookConfigActuator(entry, webhookVariables);
    }

    @Bean
    ExposedActuatorEndpoint exposedWebhookConfigActuatorEndpoint() {
        return new ExposedActuatorEndpoint(WebhookConfigActuator.class);
    }

    @Bean
    @ConfigurationProperties(prefix = "contentgrid")
    ContentgridApplicationProperties contentgridApplicationProperties() {
        return new ContentgridApplicationProperties();
    }

}
