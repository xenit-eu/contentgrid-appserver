package com.contentgrid.appserver.registry;

import com.contentgrid.appserver.application.model.values.ApplicationName;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class DefaultApplicationNameExtractor implements ApplicationNameExtractor {
    public ApplicationName extract(HttpServletRequest request) {
        // Apps are strictly single-tenant for now
        return ApplicationName.of("default");
    }
}
