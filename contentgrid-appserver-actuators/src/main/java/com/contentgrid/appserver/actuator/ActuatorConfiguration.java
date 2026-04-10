package com.contentgrid.appserver.actuator;

import com.contentgrid.appserver.actuator.policy.PolicyActuator;
import com.contentgrid.appserver.actuator.policy.PolicyVariables;
import com.contentgrid.appserver.actuator.webhooks.WebhookConfigActuator;
import com.contentgrid.appserver.actuator.webhooks.WebhookVariables;
import com.contentgrid.appserver.infrastructure.api.Artifact;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntry;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest.EndpointRequestMatcher;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.actuate.metrics.MetricsEndpoint;
import org.springframework.boot.actuate.metrics.export.prometheus.PrometheusScrapeEndpoint;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

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
    @ConfigurationProperties(prefix = "contentgrid")
    ContentgridApplicationProperties contentgridApplicationProperties() {
        return new ContentgridApplicationProperties();
    }

    @Configuration
    public static class ActuatorEndpointsWebSecurityConfiguration {
        /**
         * List of publicly accessible management endpoints
         */
        private static final EndpointRequestMatcher PUBLIC_ENDPOINTS = EndpointRequest.to(
                InfoEndpoint.class,
                HealthEndpoint.class
        );

        /**
         * List of management metrics endpoints, allowed when the management port and server port are different.
         */
        private static final EndpointRequestMatcher ALLOWED_ACTUATOR_ENDPOINTS = EndpointRequest.to(
                MetricsEndpoint.class,
                PrometheusScrapeEndpoint.class,
                PolicyActuator.class,
                WebhookConfigActuator.class
        );

        @Bean
        SecurityFilterChain actuatorEndpointsSecurityFilterChain(HttpSecurity http, Environment environment) throws Exception {

            http
                    .securityMatcher(EndpointRequest.toAnyEndpoint())
                    .authorizeHttpRequests((requests) -> requests.requestMatchers(
                            PUBLIC_ENDPOINTS,
                            new AndRequestMatcher(
                                    ALLOWED_ACTUATOR_ENDPOINTS,
                                    request -> ManagementPortType.get(environment) == ManagementPortType.DIFFERENT
                            ),
                            new AndRequestMatcher(
                                    EndpointRequest.toAnyEndpoint(),
                                    new LoopbackInetAddressMatcher()
                            )).permitAll());

            // all the other /actuator endpoints fall through
            return http.build();
        }

        private static class LoopbackInetAddressMatcher implements RequestMatcher {

            @Override
            public boolean matches(HttpServletRequest request) {
                return isLoopbackAddress(request.getRemoteAddr());
            }

            boolean isLoopbackAddress(String address) {
                try {
                    var remoteAddress = InetAddress.getByName(address);
                    return remoteAddress.isLoopbackAddress();
                } catch (UnknownHostException ex) {
                    return false;
                }
            }
        }

    }
}
