package com.contentgrid.appserver.registry;

import com.contentgrid.appserver.application.model.Application;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SingleApplicationResolver implements ApplicationResolver {

    private final Application application;

    @Override
    public Application resolve(HttpServletRequest request) {
        return application;
    }
}
