package com.contentgrid.appserver.actuator;

import com.contentgrid.appserver.actuator.policy.IsOpaSidecarModeCondition;
import com.contentgrid.appserver.actuator.policy.PolicyActuator;
import com.contentgrid.appserver.actuator.policy.PolicyBundleActuator;
import com.contentgrid.appserver.actuator.policy.PolicyBundleSource;
import com.contentgrid.appserver.actuator.webhooks.WebhookConfigActuator;
import com.contentgrid.appserver.actuator.webhooks.WebhookVariables;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import com.contentgrid.common.spring.actuators.ExposedActuatorEndpoint;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ActuatorConfiguration {

    @Bean
    @ConditionalOnProperty(IsOpaSidecarModeCondition.PROPERTY_POLICY_PACKAGE)
    PolicyActuator policyActuator(BlueprintArtifact blueprintArtifact, ContentgridApplicationProperties properties) {
        return new PolicyActuator(blueprintArtifact, properties.getSystem().getPolicyPackage());
    }

    @Bean
    @ConditionalOnBean(PolicyActuator.class)
    ExposedActuatorEndpoint exposedPolicyActuatorEndpoint() {
        return new ExposedActuatorEndpoint(PolicyActuator.class);
    }

    @Bean
    PolicyBundleSource policyBundleSource(BlueprintArtifact blueprintArtifact,
            ContentgridApplicationProperties properties) {
        return new PolicyBundleSource(blueprintArtifact, properties.getSystem().getPolicyPackage());
    }

    @Bean
    PolicyBundleActuator policyBundleActuator(PolicyBundleSource policyBundleSource) {
        return new PolicyBundleActuator(policyBundleSource);
    }

    @Bean
    ExposedActuatorEndpoint exposedPolicyBundleActuatorEndpoint() {
        return new ExposedActuatorEndpoint(PolicyBundleActuator.class);
    }

    @Bean
    WebhookVariables webhookVariables(ContentgridApplicationProperties applicationProperties) {
        return WebhookVariables.builder()
                .systemProperties(applicationProperties.getSystem())
                .userVariables(applicationProperties.getVariables())
                .build();
    }

    @Bean
    WebhookConfigActuator webHooksConfigActuator(WebhookVariables webhookVariables, BlueprintArtifact blueprintArtifact) {
        return new WebhookConfigActuator(blueprintArtifact, webhookVariables);
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
