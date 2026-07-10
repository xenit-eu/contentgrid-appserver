package com.contentgrid.appserver.autoconfigure.security.authority;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.autoconfigure.security.authority.Actor.ActorType;
import java.time.Instant;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;

class UserGrantedAuthorityConverterTest {

    private static final String USER_ISSUER = "https://tenant.example.com";
    private static final String EXTENSION_ISSUER = "https://extensions.example.com";

    private ActorConverter extensionActorConverter() {
        var converter = new ActorConverter(Predicate.isEqual(EXTENSION_ISSUER), ActorType.EXTENSION,
                claims -> Map.of(JwtClaimNames.SUB, claims.getClaimAsString(JwtClaimNames.SUB),
                        JwtClaimNames.ISS, claims.getClaimAsString(JwtClaimNames.ISS)));
        converter.setParentActorConverter(converter);
        return converter;
    }

    private ActorConverter userActorConverter() {
        var converter = new ActorConverter(Predicate.isEqual(USER_ISSUER), ActorType.USER,
                claims -> Map.of(JwtClaimNames.SUB, claims.getClaimAsString(JwtClaimNames.SUB),
                        JwtClaimNames.ISS, claims.getClaimAsString(JwtClaimNames.ISS)));
        converter.setParentActorConverter(extensionActorConverter());
        return converter;
    }

    private Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claims(c -> c.putAll(claims))
                .issuedAt(Instant.EPOCH)
                .expiresAt(Instant.EPOCH.plusSeconds(60))
                .build();
    }

    @Test
    void plainUserToken_yieldsPrincipalOnly() {
        var converter = new UserGrantedAuthorityConverter(userActorConverter());

        var authorities = converter.convert(jwt(Map.of(
                JwtClaimNames.ISS, USER_ISSUER,
                JwtClaimNames.SUB, "user-1"
        )));

        assertThat(authorities).singleElement().satisfies(authority -> {
            var details = (AuthenticationDetails) authority;
            assertThat(details).isInstanceOf(PrincipalAuthenticationDetailsGrantedAuthority.class);
            assertThat(details.principal().type()).isEqualTo(ActorType.USER);
            assertThat(details.principal().claims()).containsEntry(JwtClaimNames.SUB, "user-1");
            assertThat(details.principal().parent()).isNull();
            assertThat(details.actor()).isNull();
        });
    }

    @Test
    void tokenWithActClaim_yieldsDelegatedPrincipalAndActor() {
        var converter = new UserGrantedAuthorityConverter(userActorConverter());

        var authorities = converter.convert(jwt(Map.of(
                JwtClaimNames.ISS, USER_ISSUER,
                JwtClaimNames.SUB, "user-1",
                "act", Map.of(
                        JwtClaimNames.ISS, EXTENSION_ISSUER,
                        JwtClaimNames.SUB, "extension-1"
                )
        )));

        assertThat(authorities).singleElement().satisfies(authority -> {
            var details = (AuthenticationDetails) authority;
            assertThat(details).isInstanceOf(DelegatedAuthenticationDetailsGrantedAuthority.class);

            assertThat(details.principal().type()).isEqualTo(ActorType.USER);
            assertThat(details.principal().claims()).containsEntry(JwtClaimNames.SUB, "user-1");
            assertThat(details.principal().parent()).isNull();

            assertThat(details.actor().type()).isEqualTo(ActorType.EXTENSION);
            assertThat(details.actor().claims()).containsEntry(JwtClaimNames.SUB, "extension-1");
        });
    }

    @Test
    void tokenWithNestedActClaims_resolvesActorChain() {
        var converter = new UserGrantedAuthorityConverter(userActorConverter());

        var authorities = converter.convert(jwt(Map.of(
                JwtClaimNames.ISS, USER_ISSUER,
                JwtClaimNames.SUB, "user-1",
                "act", Map.of(
                        JwtClaimNames.ISS, EXTENSION_ISSUER,
                        JwtClaimNames.SUB, "extension-1",
                        "act", Map.of(
                                JwtClaimNames.ISS, EXTENSION_ISSUER,
                                JwtClaimNames.SUB, "extension-2"
                        )
                )
        )));

        assertThat(authorities).singleElement().satisfies(authority -> {
            var details = (AuthenticationDetails) authority;
            assertThat(details.actor().claims()).containsEntry(JwtClaimNames.SUB, "extension-1");
            assertThat(details.actor().parent()).isNotNull();
            assertThat(details.actor().parent().claims()).containsEntry(JwtClaimNames.SUB, "extension-2");
        });
    }
}
