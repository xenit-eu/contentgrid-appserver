package com.contentgrid.appserver.autoconfigure.security;

import com.contentgrid.appserver.actuator.policy.IsOpaSidecarModeCondition;
import com.contentgrid.appserver.security.authority.GatewayJwtAuthenticationDetailsConverter;
import com.contentgrid.thunx.webmvc.autoconfigure.WebMvcAbacAutoConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

@Slf4j
@AutoConfiguration(before = SecurityAutoConfiguration.class, after = WebMvcAbacAutoConfiguration.class)
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass(SecurityFilterChain.class)
public class DefaultSecurityAutoConfiguration {

    @Bean
    @ConditionalOnBean({AuthorizationManager.class, JwtDecoder.class})
    SecurityFilterChain opaSecurityFilterChain(
            HttpSecurity http,
            AuthorizationManager<RequestAuthorizationContext> policyAuthorizationManager
    ) {
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().access(policyAuthorizationManager));
        http.oauth2ResourceServer(oauth2 -> oauth2.jwt(
                jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    @ConditionalOnBean(JwtDecoder.class)
    @ConditionalOnMissingBean
    SecurityFilterChain centralizedOpaSecurityFilterChain(HttpSecurity http) {
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated());
        http.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /**
     * Last resort: reached only when neither {@link #opaSecurityFilterChain} nor
     * {@link #centralizedOpaSecurityFilterChain} created a chain, which (given their conditions) only happens
     * when no {@link JwtDecoder} is available. Without one there is no way to decode and verify the JWT, so
     * this requires some non-anonymous authentication, for instance by {@link AnonymousHttpConfigurer}.
     */
    @Bean
    @ConditionalOnMissingBean
    SecurityFilterChain noJwtDecoderSecurityFilterChain(HttpSecurity http) {
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated());
        return http.build();
    }

    /**
     * Fails application startup outright if a policy {@link AuthorizationManager} bean exists (from
     * {@code contentgrid.thunx.abac.source=opa} / {@code opa.service.url}) while {@code contentgrid.system.policyPackage}
     * is also set, i.e. centralized/Solon mode. That combination means the app would silently authorize every
     * request with {@code authenticated()} only, ignoring the configured OPA policy manager, which is never what's
     * intended.
     * <p>
     * Gated by {@code @ConditionalOnExpression}/{@code @ConditionalOnBean} rather than an injected runtime check:
     * this bean, and therefore its failure, only exists when both conditions already hold.
     */
    @Bean
    @ConditionalOnExpression(
            "T(org.springframework.util.StringUtils).hasText('${" + IsOpaSidecarModeCondition.PROPERTY_POLICY_PACKAGE + ":}')")
    @ConditionalOnBean(AuthorizationManager.class)
    InitializingBean policyAuthorizationManagerConflictValidator() {
        return () -> {
            throw new IllegalStateException(
                    "A policy AuthorizationManager bean is present (from contentgrid.thunx.abac.source=opa && "
                            + "opa.service.url), but contentgrid.system.policyPackage is also set, which puts "
                            + "this app in centralized OPA mode. These are mutually exclusive: "
                            + "unset contentgrid.system.policyPackage for sidecar mode, "
                            + "or remove the OPA client configuration for centralized mode.");
        };
    }

    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new GatewayJwtAuthenticationDetailsConverter());
        return converter;
    }
}
