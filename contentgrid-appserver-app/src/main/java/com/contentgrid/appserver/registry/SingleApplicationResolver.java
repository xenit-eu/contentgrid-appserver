package com.contentgrid.appserver.registry;

import com.contentgrid.appserver.application.model.Application;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SingleApplicationResolver implements ApplicationResolver {

    @Getter
    private final Application application;

    @Override
    public Application resolve(HttpServletRequest request) {
        return application;
    }
}
