package com.contentgrid.appserver.autoconfigure.security;

import com.contentgrid.appserver.security.opa.OpaSidecarFeature;
import com.contentgrid.appserver.security.authority.GatewayJwtAuthenticationDetailsConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

@Slf4j
@AutoConfiguration(before = SecurityAutoConfiguration.class)
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass(SecurityFilterChain.class)
@EnableWebSecurity
public class DefaultSecurityAutoConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<JwtDecoder> jwtDecoder,
            ObjectProvider<AuthorizationManager<RequestAuthorizationContext>> policyAuthorizationManager,
            OpaSidecarFeature sidecarFeature
    ) {
        if (sidecarFeature.isActive()) {
            var authorizationManager = policyAuthorizationManager.getIfAvailable();
            if (authorizationManager != null) {
                http.authorizeHttpRequests(authorize -> authorize.anyRequest().access(authorizationManager));
            } else {
                log.warn("No authorizationManager found, authorization by OPA will be skipped.");
                http.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated());
            }
            var decoder = jwtDecoder.getIfAvailable();
            if (decoder != null) {
                http.oauth2ResourceServer(oauth2 -> oauth2.jwt(
                        jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
            } else {
                log.warn("No jwtDecoder found, requests will be done anonymously.");
            }
        } else {
            if (policyAuthorizationManager.getIfAvailable() != null) {
                log.warn("contentgrid.thunx.abac.source=opa and opa.service.url are configured, but "
                        + "contentgrid.system.policyPackage is also set (centralized/Solon mode, where this app "
                        + "never uploads its policy to that OPA). The policy-based AuthorizationManager is ignored "
                        + "and we fall back to authenticated()-only authorization.");
            }

            http.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated());

            if (jwtDecoder.getIfAvailable() != null) {
                http.oauth2ResourceServer((oauth2) -> oauth2.jwt(Customizer.withDefaults()));
            }
        }
        return http.build();
    }

    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new GatewayJwtAuthenticationDetailsConverter());
        return converter;
    }
}
