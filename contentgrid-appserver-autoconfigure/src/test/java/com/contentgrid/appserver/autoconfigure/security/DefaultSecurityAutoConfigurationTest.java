package com.contentgrid.appserver.autoconfigure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.contentgrid.appserver.autoconfigure.opa.OpaSidecarFeatureAutoConfiguration;
import com.contentgrid.appserver.security.authority.AuthenticationDetails;
import com.contentgrid.appserver.security.authority.GatewayAuthClaimNames;
import com.contentgrid.appserver.security.opa.OpaSidecarFeature;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

class DefaultSecurityAutoConfigurationTest {

    private static final String USER_ISSUER = "https://tenant.example.com";

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DefaultSecurityAutoConfiguration.class,
                    OpaSidecarFeatureAutoConfiguration.class,
                    OAuth2ResourceServerAutoConfiguration.class, SecurityAutoConfiguration.class,
                    ServletWebSecurityAutoConfiguration.class
            ))
            .withUserConfiguration(StubController.class);

    @Test
    void hasSingleSecurityFilterChain() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SecurityFilterChain.class);
        });
    }

    private static Jwt gatewayShapedJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim(JwtClaimNames.ISS, USER_ISSUER)
                .claim(JwtClaimNames.SUB, "user-1")
                .claim(GatewayAuthClaimNames.AUTH_KIND, GatewayAuthClaimNames.AUTH_KIND_USER)
                .claim(GatewayAuthClaimNames.AUTH_PRINCIPAL, Map.of(
                        GatewayAuthClaimNames.KIND, GatewayAuthClaimNames.KIND_USER,
                        JwtClaimNames.SUB, "user-1"
                ))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }

    private static Jwt contractViolatingJwt() {
        // Gateway-signed, but missing the contentgrid:auth:principal claim which should be set by the gateway.
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim(JwtClaimNames.ISS, USER_ISSUER)
                .claim(JwtClaimNames.SUB, "user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }

    @Test
    void sidecarMode_wiresGatewayJwtAuthenticationDetailsConverter() {
        contextRunner
                .withBean(JwtDecoder.class, () -> token -> gatewayShapedJwt())
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    assertThat(bearerTokenRequest(context))
                            .matches(authenticated().withAuthentication(authentication ->
                                    assertThat(authentication.getAuthorities())
                                            .hasAtLeastOneElementOfType(AuthenticationDetails.class)));
                });
    }

    @Test
    void sidecarMode_rejectsContractViolatingToken_asUnauthorized() {
        contextRunner
                .withBean(JwtDecoder.class, () -> token -> contractViolatingJwt())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(bearerTokenRequest(context)).hasStatus(HttpStatus.UNAUTHORIZED);
                });
    }

    @Test
    void sidecarMode_appliesPolicyAuthorizationManager() {
        contextRunner
                .withUserConfiguration(DenyAllPolicyConfiguration.class)
                .withBean(JwtDecoder.class, () -> token -> gatewayShapedJwt())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(bearerTokenRequest(context)).hasStatus(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    void nonSidecarMode_doesNotWireGatewayJwtAuthenticationDetailsConverter() {
        contextRunner
                .withBean("opaSidecarFeatureOverride", OpaSidecarFeature.class, () -> () -> false)
                .withBean(JwtDecoder.class, () -> token -> gatewayShapedJwt())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(OpaSidecarFeature.class).isActive()).isFalse();

                    assertThat(bearerTokenRequest(context))
                            .matches(authenticated().withAuthentication(authentication ->
                                    assertThat(authentication.getAuthorities())
                                            .noneMatch(AuthenticationDetails.class::isInstance)));
                });
    }

    @Test
    void nonSidecarMode_withPolicyAuthorizationManager_failsFast() {
        contextRunner
                .withBean("opaSidecarFeatureOverride", OpaSidecarFeature.class, () -> () -> false)
                .withUserConfiguration(DenyAllPolicyConfiguration.class)
                .withBean(JwtDecoder.class, () -> token -> gatewayShapedJwt())
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure()
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("contentgrid.system.policyPackage");
                });
    }

    @Test
    void withoutJwtDecoder_leavesRequestsUnauthenticated() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            assertThat(bearerTokenRequest(context))
                    .matches(unauthenticated())
                    .hasStatus(HttpStatus.FORBIDDEN);
        });
    }

    /**
     * Drives a request through the real, fully assembled {@code springSecurityFilterChain}, without starting a
     * servlet container. A request that is not rejected by the chain reaches {@link StubController} and gets a 200.
     */
    private static MvcTestResult bearerTokenRequest(WebApplicationContext context) {
        var mockMvc = MockMvcTester.from(context, builder -> builder.apply(springSecurity()).build());
        return mockMvc.get().uri("/").header(HttpHeaders.AUTHORIZATION, "Bearer test-token").exchange();
    }

    /**
     * Gives every request a real endpoint to land on, so that "allowed by the security chain" shows up as a 200.
     */
    @RestController
    static class StubController {

        @GetMapping("/")
        String root() {
            return "ok";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DenyAllPolicyConfiguration {

        /**
         * Stands in for thunx's {@code PolicyAuthorizationManager}.
         */
        @Bean
        AuthorizationManager<RequestAuthorizationContext> policyAuthorizationManager() {
            return (authentication, requestAuthorizationContext) -> new AuthorizationDecision(false);
        }
    }
}
