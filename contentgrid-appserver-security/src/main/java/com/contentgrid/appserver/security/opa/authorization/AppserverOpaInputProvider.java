package com.contentgrid.appserver.security.opa.authorization;

import com.contentgrid.thunx.pdp.opa.OpaInputProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.security.core.Authentication;

public class AppserverOpaInputProvider implements OpaInputProvider<Authentication, HttpServletRequest> {

    @Override
    public Map<String, Object> createInput(Authentication authentication, HttpServletRequest request) {
        return Map.of(
                "auth", AuthenticationModel.from(authentication),
                "request", RequestModel.from(request)
        );
    }
}
