package com.contentgrid.appserver.autoconfigure.security;

import com.contentgrid.appserver.autoconfigure.opa.OpaSidecarFeature;
import com.contentgrid.appserver.autoconfigure.security.authority.Actor.ActorType;
import com.contentgrid.appserver.autoconfigure.security.authority.ActorConverter;
import com.contentgrid.appserver.autoconfigure.security.authority.ClaimUtil;
import com.contentgrid.appserver.autoconfigure.security.authority.UserGrantedAuthorityConverter;
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

/**
 * Security configuration for the opa sidecar configuration: the appserver never talks to an end-user or
 * extension identity provider directly. All authentication happens in the ContentGrid gateway, which
 * validates the original tokens and mints a single gateway-signed JWT that is forwarded to the appserver.
 * <p>
 * That minted token:
 * <ul>
 *     <li>is signed with the gateway's own 'apps' key, exposed on the gateway's JWKS endpoint;</li>
 *     <li>copies the original {@code iss} claim value from the upstream token, so {@code iss} varies per
 *     application IdP — issuer-based validation or OIDC discovery is therefore NOT possible;</li>
 *     <li>sets {@code aud} to {@code contentgrid:app:{applicationId}:{deploymentId}}, identifying this
 *     specific application deployment as the intended recipient;</li>
 *     <li>reconstructs the RFC 8693 {@code act} actor chain (nested {@code act} claims) when an extension
 *     acts on behalf of a user.</li>
 * </ul>
 * Consequently, the appserver MUST be configured with:
 * <ul>
 *     <li>{@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri} pointing at the gateway's JWKS
 *     endpoint (NOT {@code issuer-uri}, since {@code iss} is not a stable/discoverable value here); and</li>
 *     <li>{@code spring.security.oauth2.resourceserver.jwt.audiences} set to this deployment's
 *     {@code contentgrid:app:{applicationId}:{deploymentId}} identifier, so tokens minted for a different
 *     application/deployment are rejected.</li>
 * </ul>
 * Spring Boot's autoconfigured {@code JwtDecoder} (see
 * {@code OAuth2ResourceServerJwtConfiguration.JwtDecoderConfiguration#jwtDecoderByJwkKeySetUri}) natively
 * adds an {@code aud} {@link org.springframework.security.oauth2.jwt.JwtClaimValidator} whenever the
 * {@code audiences} property is non-empty, so no custom {@code JwtDecoder} customization is needed here.
 */
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
            OpaSidecarFeature sidecarFeature,
            ActorConverter userActorConverter
    ) {
        if (sidecarFeature.isActive()) {
            var authorizationManager = policyAuthorizationManager.getIfAvailable();
            if (authorizationManager != null) {
                http.authorizeHttpRequests(authorize -> authorize.anyRequest().access(authorizationManager));
            } else {
                http.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated());
            }
            var decoder = jwtDecoder.getIfAvailable();
            if (decoder != null) {
                http.oauth2ResourceServer(oauth2 -> oauth2.jwt(
                        jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter(userActorConverter))));
            }
        } else {
            if (policyAuthorizationManager.getIfAvailable() != null) {
                log.warn("contentgrid.thunx.abac.source=opa and opa.service.url are configured, but "
                        + "contentgrid.system.policyPackage is also set (centralized/Solon mode, where this app "
                        + "never uploads its policy to that OPA) - ignoring the policy-based AuthorizationManager "
                        + "and falling back to authenticated()-only authorization.");
            }

            http.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated());

            if (jwtDecoder.getIfAvailable() != null) {
                http.oauth2ResourceServer((oauth2) -> oauth2.jwt(Customizer.withDefaults()));
            }
        }
        return http.build();
    }

    private static JwtAuthenticationConverter jwtAuthenticationConverter(ActorConverter actorConverter) {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new UserGrantedAuthorityConverter(actorConverter));
        return converter;
    }

    @Bean
    ActorConverter userActorConverter() {
        // The gateway already processes the actor chain, so we can rely on the position here. The top-level
        // token is always the USER, and every (possibly nested) 'act' entry is an EXTENSION acting on
        // the user's behalf.
        var extensionActorConverter = new ActorConverter(issuer -> true, ActorType.EXTENSION,
                ClaimUtil::extensionSystemClaims);
        extensionActorConverter.setParentActorConverter(extensionActorConverter);

        var userActorConverter = new ActorConverter(issuer -> true, ActorType.USER, ClaimUtil::userClaims);
        userActorConverter.setParentActorConverter(extensionActorConverter);
        return userActorConverter;
    }
}
