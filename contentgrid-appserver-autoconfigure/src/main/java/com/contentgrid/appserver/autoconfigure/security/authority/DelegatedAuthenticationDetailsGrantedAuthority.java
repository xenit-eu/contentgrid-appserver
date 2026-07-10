package com.contentgrid.appserver.autoconfigure.security.authority;

import lombok.NonNull;
import org.springframework.security.core.GrantedAuthority;

public record DelegatedAuthenticationDetailsGrantedAuthority(@NonNull Actor principal, @NonNull Actor actor) implements
        AuthenticationDetails, GrantedAuthority {

    public DelegatedAuthenticationDetailsGrantedAuthority {
        if (principal.parent() != null) {
            throw new IllegalArgumentException("Principal actor can not have a parent");
        }
    }

    @Override
    public String getAuthority() {
        return "AUTHENTICATION_ATTRIBUTES";
    }
}
