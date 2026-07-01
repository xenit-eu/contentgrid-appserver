package com.contentgrid.appserver.autoconfigure.security.authority;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;

@Getter
@RequiredArgsConstructor
public class PrincipalAuthenticationDetailsGrantedAuthority implements AuthenticationDetails, GrantedAuthority {

    @NonNull
    private final Actor principal;

    @Override
    public String getAuthority() {
        return "AUTHENTICATION_ATTRIBUTES";
    }
}
