package com.contentgrid.appserver.security.authority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contentgrid.appserver.security.authority.Actor.ActorType;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

class GatewayJwtAuthenticationDetailsConverterTest {

    private final GatewayJwtAuthenticationDetailsConverter converter = new GatewayJwtAuthenticationDetailsConverter();

    private static Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claims(c -> c.putAll(claims))
                .issuedAt(Instant.EPOCH)
                .expiresAt(Instant.EPOCH.plusSeconds(60))
                .build();
    }

    @Test
    void plainUserToken_yieldsUserPrincipalOnly() {
        var token = jwt(Map.of(
                JwtClaimNames.ISS, "https://tenant.example.com",
                JwtClaimNames.SUB, "user-1",
                GatewayAuthClaimNames.AUTH_KIND, GatewayAuthClaimNames.AUTH_KIND_USER,
                GatewayAuthClaimNames.AUTH_PRINCIPAL, Map.of(
                        GatewayAuthClaimNames.KIND, GatewayAuthClaimNames.KIND_USER,
                        JwtClaimNames.ISS, "https://tenant.example.com",
                        JwtClaimNames.SUB, "user-1",
                        "email", "alice@example.com"
                )
        ));

        var authorities = converter.convert(token);

        assertThat(authorities).singleElement().satisfies(authority -> {
            var details = (AuthenticationDetails) authority;
            assertThat(details.getPrincipal().type()).isEqualTo(ActorType.USER);
            assertThat(details.getPrincipal().claims().getClaims()).containsExactlyInAnyOrderEntriesOf(Map.of(
                    JwtClaimNames.ISS, "https://tenant.example.com",
                    JwtClaimNames.SUB, "user-1",
                    "email", "alice@example.com"
            ));
            assertThat(details.getPrincipal().parent()).isNull();
            assertThat(details.getActor()).isNull();
        });
    }

    @Test
    void systemToken_yieldsExtensionPrincipalOnly() {
        var token = jwt(Map.of(
                JwtClaimNames.ISS, "https://extensions.example.com",
                JwtClaimNames.SUB, "extension-1",
                GatewayAuthClaimNames.AUTH_KIND, GatewayAuthClaimNames.AUTH_KIND_SYSTEM,
                GatewayAuthClaimNames.AUTH_PRINCIPAL, Map.of(
                        GatewayAuthClaimNames.KIND, GatewayAuthClaimNames.KIND_EXTENSION,
                        JwtClaimNames.ISS, "https://extensions.example.com",
                        JwtClaimNames.SUB, "extension-1"
                )
        ));

        var authorities = converter.convert(token);

        assertThat(authorities).singleElement().satisfies(authority -> {
            var details = (AuthenticationDetails) authority;
            assertThat(details.getPrincipal().type()).isEqualTo(ActorType.EXTENSION);
            assertThat(details.getPrincipal().claims().getClaims()).containsExactlyInAnyOrderEntriesOf(Map.of(
                    JwtClaimNames.ISS, "https://extensions.example.com",
                    JwtClaimNames.SUB, "extension-1"
            ));
            assertThat(details.getActor()).isNull();
        });
    }

    @Test
    void delegatedToken_withNestedActChain_resolvesActorChain() {
        var token = jwt(Map.of(
                JwtClaimNames.ISS, "https://tenant.example.com",
                JwtClaimNames.SUB, "user-1",
                GatewayAuthClaimNames.AUTH_KIND, GatewayAuthClaimNames.AUTH_KIND_DELEGATED,
                GatewayAuthClaimNames.AUTH_PRINCIPAL, Map.of(
                        GatewayAuthClaimNames.KIND, GatewayAuthClaimNames.KIND_USER,
                        JwtClaimNames.ISS, "https://tenant.example.com",
                        JwtClaimNames.SUB, "user-1",
                        "contentgrid:claim1", "value1"
                ),
                GatewayAuthClaimNames.ACT, Map.of(
                        GatewayAuthClaimNames.KIND, GatewayAuthClaimNames.KIND_EXTENSION,
                        JwtClaimNames.ISS, "https://extensions.example.com",
                        JwtClaimNames.SUB, "extension-1",
                        GatewayAuthClaimNames.ACT, Map.of(
                                GatewayAuthClaimNames.KIND, GatewayAuthClaimNames.KIND_EXTENSION,
                                JwtClaimNames.ISS, "https://extensions.example.com",
                                JwtClaimNames.SUB, "extension-2"
                        )
                )
        ));

        var authorities = converter.convert(token);

        assertThat(authorities).singleElement().satisfies(authority -> {
            var details = (AuthenticationDetails) authority;

            assertThat(details.getPrincipal().type()).isEqualTo(ActorType.USER);
            assertThat(details.getPrincipal().claims().getClaims()).containsExactlyInAnyOrderEntriesOf(Map.of(
                    JwtClaimNames.ISS, "https://tenant.example.com",
                    JwtClaimNames.SUB, "user-1",
                    "contentgrid:claim1", "value1"
            ));
            assertThat(details.getPrincipal().parent()).isNull();

            var actor = details.getActor();
            assertThat(actor).isNotNull();
            var actorParent = details.getActor().parent();
            assertThat(actorParent).isNotNull();

            assertThat(actor.type()).isEqualTo(ActorType.EXTENSION);
            assertThat(actor.claims().getClaimAsString(JwtClaimNames.SUB)).isEqualTo("extension-1");
            assertThat(actorParent.type()).isEqualTo(ActorType.EXTENSION);
            assertThat(actorParent.claims().getClaimAsString(JwtClaimNames.SUB))
                    .isEqualTo("extension-2");
            assertThat(actorParent.parent()).isNull();
        });
    }

    // A gateway-signed token that violates the minting contract must be rejected as an invalid token (401),
    // never classified leniently or crash with an unhandled exception (500).
    @ParameterizedTest
    @MethodSource("rejectedTokens")
    void rejectsMalformedTokens(Map<String, Object> claims, String expectedMessageFragment) {
        var token = jwt(claims);

        var thrown = assertThatThrownBy(() -> converter.convert(token))
                .isInstanceOf(InvalidBearerTokenException.class);

        if (expectedMessageFragment != null) {
            thrown.hasMessageContaining(expectedMessageFragment);
        }
    }

    static Stream<Arguments> rejectedTokens() {
        return Stream.of(
                Arguments.argumentSet(
                        "missing principal claim",
                        Map.of(
                                JwtClaimNames.ISS, "https://tenant.example.com",
                                JwtClaimNames.SUB, "user-1"
                        ),
                        GatewayAuthClaimNames.AUTH_PRINCIPAL
                ),
                Arguments.argumentSet(
                        "principal claim is not an object",
                        Map.of(
                                JwtClaimNames.SUB, "user-1",
                                GatewayAuthClaimNames.AUTH_PRINCIPAL, "not-an-object"
                        ),
                        null
                ),
                Arguments.argumentSet(
                        "principal object missing kind",
                        Map.of(
                                JwtClaimNames.SUB, "user-1",
                                GatewayAuthClaimNames.AUTH_PRINCIPAL, Map.of(
                                        JwtClaimNames.SUB, "user-1"
                                )
                        ),
                        GatewayAuthClaimNames.KIND
                ),
                Arguments.argumentSet(
                        "actor object with unknown kind",
                        Map.of(
                                JwtClaimNames.SUB, "user-1",
                                GatewayAuthClaimNames.AUTH_KIND, GatewayAuthClaimNames.AUTH_KIND_DELEGATED,
                                GatewayAuthClaimNames.AUTH_PRINCIPAL, Map.of(
                                        GatewayAuthClaimNames.KIND, GatewayAuthClaimNames.KIND_USER,
                                        JwtClaimNames.SUB, "user-1"
                                ),
                                GatewayAuthClaimNames.ACT, Map.of(
                                        GatewayAuthClaimNames.KIND, "robot",
                                        JwtClaimNames.SUB, "extension-1"
                                )
                        ),
                        "robot"
                ),
                Arguments.argumentSet(
                        "delegated kind without act claim",
                        Map.of(
                                JwtClaimNames.SUB, "user-1",
                                GatewayAuthClaimNames.AUTH_KIND, GatewayAuthClaimNames.AUTH_KIND_DELEGATED,
                                GatewayAuthClaimNames.AUTH_PRINCIPAL, Map.of(
                                        GatewayAuthClaimNames.KIND, GatewayAuthClaimNames.KIND_USER,
                                        JwtClaimNames.SUB, "user-1"
                                )
                        ),
                        GatewayAuthClaimNames.AUTH_KIND
                ),
                Arguments.argumentSet(
                        "act claim on non-delegated token",
                        Map.of(
                                JwtClaimNames.SUB, "user-1",
                                GatewayAuthClaimNames.AUTH_KIND, GatewayAuthClaimNames.AUTH_KIND_USER,
                                GatewayAuthClaimNames.AUTH_PRINCIPAL, Map.of(
                                        GatewayAuthClaimNames.KIND, GatewayAuthClaimNames.KIND_USER,
                                        JwtClaimNames.SUB, "user-1"
                                ),
                                GatewayAuthClaimNames.ACT, Map.of(
                                        GatewayAuthClaimNames.KIND, GatewayAuthClaimNames.KIND_EXTENSION,
                                        JwtClaimNames.SUB, "extension-1"
                                )
                        ),
                        GatewayAuthClaimNames.AUTH_KIND
                ),
                Arguments.argumentSet(
                        "empty act claim",
                        Map.of(
                                JwtClaimNames.SUB, "user-1",
                                GatewayAuthClaimNames.AUTH_KIND, GatewayAuthClaimNames.AUTH_KIND_USER,
                                GatewayAuthClaimNames.AUTH_PRINCIPAL, Map.of(
                                        GatewayAuthClaimNames.KIND, GatewayAuthClaimNames.KIND_USER,
                                        JwtClaimNames.SUB, "user-1"
                                ),
                                GatewayAuthClaimNames.ACT, Map.of()
                        ),
                        GatewayAuthClaimNames.AUTH_KIND
                )
        );
    }
}
