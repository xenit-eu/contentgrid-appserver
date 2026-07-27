package com.contentgrid.appserver.security.authority;

import lombok.Getter;
import lombok.NonNull;
import org.springframework.security.core.GrantedAuthority;

// Duplicated from the gateway
@Getter
abstract class AbstractAuthenticationDetailsGrantedAuthority implements AuthenticationDetails, GrantedAuthority {

    @NonNull
    private final Actor principal;

    protected AbstractAuthenticationDetailsGrantedAuthority(@NonNull Actor principal) {
        if (principal.parent() != null) {
            throw new IllegalArgumentException("Principal actor can not have a parent");
        }
        this.principal = principal;
    }

    @Override
    public String getAuthority() {
        return null;
    }
}
