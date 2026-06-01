package com.contentgrid.appserver.actuator;

import com.contentgrid.appserver.actuator.policy.PolicyActuator;
import com.contentgrid.appserver.actuator.policy.PolicyVariables;
import com.contentgrid.appserver.actuator.webhooks.WebhookConfigActuator;
import com.contentgrid.appserver.actuator.webhooks.WebhookVariables;
import com.contentgrid.common.spring.actuators.ExposedActuatorEndpoint;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ActuatorConfiguration {

    private final ApplicationContext applicationContext;

    @Bean
    PolicyVariables policyVariables(ContentgridApplicationProperties applicationProperties) {
        return PolicyVariables.builder()
                .policyPackageName(applicationProperties.getSystem().getPolicyPackage())
                .build();
    }

    @Bean
    PolicyActuator policyActuator(PolicyVariables policyVariables) {
        return new PolicyActuator(applicationContext.getResource("classpath:rego/policy.rego"), policyVariables);
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
    WebhookConfigActuator webHooksConfigActuator(WebhookVariables webhookVariables) {
        return new WebhookConfigActuator(applicationContext.getResource("classpath:eventhandler/webhooks.json"),
                webhookVariables);
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
