package com.contentgrid.appserver.actuator;

import com.contentgrid.appserver.actuator.policy.PolicyActuator;
import com.contentgrid.appserver.actuator.policy.PolicyVariables;
import com.contentgrid.appserver.actuator.webhooks.WebhookConfigActuator;
import com.contentgrid.appserver.actuator.webhooks.WebhookVariables;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

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
    @ConfigurationProperties(prefix = "contentgrid")
    ContentgridApplicationProperties contentgridApplicationProperties() {
        return new ContentgridApplicationProperties();
    }

    @Configuration
    @EnableWebSecurity
    public static class ActuatorEndpointsWebSecurityConfiguration {

        @Bean
        SecurityFilterChain actuatorEndpointsSecurityFilterChain(HttpSecurity http) throws Exception {

            http
                    .securityMatcher("/actuator/**")
                    .authorizeHttpRequests((requests) -> requests
                            .requestMatchers("/actuator/info").permitAll()
                            .requestMatchers("/actuator/health/**").permitAll()
                            .requestMatchers("/actuator/health").permitAll()
                            .requestMatchers("/actuator").permitAll()
                            .anyRequest().authenticated()
                    );

            return http.build();
        }

    }
}
