package com.contentgrid.appserver.security.authority;

import lombok.experimental.UtilityClass;

/**
 * Names of the claims that the ContentGrid gateway puts on the JWT it mints for the appserver.
 */
@UtilityClass
public class GatewayAuthClaimNames {

    /**
     * RFC 8693 actor chain. The outermost {@code act} object is the current actor; a nested {@code act} member
     * inside it identifies the actor it is itself acting on behalf of (a prior actor). Each actor object also
     * carries a {@value #KIND} member.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc8693.html#name-act-actor-claim">RFC8693</a>
     */
    public static final String ACT = "act";

    /**
     * Discriminates the kind of an actor/principal object: either {@value #KIND_USER} or {@value #KIND_EXTENSION}.
     * Present as a member on both the {@value #AUTH_PRINCIPAL} object and every object in the {@value #ACT} chain.
     */
    public static final String KIND = "kind";

    public static final String KIND_USER = "user";
    public static final String KIND_EXTENSION = "extension";

    /**
     * The high-level authentication kind the gateway decided on for this request: {@value #AUTH_KIND_USER},
     * {@value #AUTH_KIND_SYSTEM} or {@value #AUTH_KIND_DELEGATED}. Anonymous requests carry no bearer token at
     * all, so there is no "anonymous" value here.
     */
    public static final String AUTH_KIND = "contentgrid:auth:kind";

    public static final String AUTH_KIND_USER = "user";
    public static final String AUTH_KIND_SYSTEM = "system";
    public static final String AUTH_KIND_DELEGATED = "delegated";

    /**
     * The processed principal, as a JSON object with a {@value #KIND} member ({@value #KIND_USER} or
     * {@value #KIND_EXTENSION}) plus the principal's own claims ({@code iss}, {@code sub}, {@code name},
     * {@code email}, {@code contentgrid:*} attributes). For delegated tokens, these are the decrypted end-user
     * claims; for system tokens, just the {@code iss}/{@code sub} of the extension.
     */
    public static final String AUTH_PRINCIPAL = "contentgrid:auth:principal";
}
