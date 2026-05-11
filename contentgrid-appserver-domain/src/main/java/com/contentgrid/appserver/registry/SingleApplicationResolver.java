package com.contentgrid.appserver.registry;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SingleApplicationResolver implements ApplicationResolver {

    @Getter
    private final Application application;

    @Override
    public Application resolve(ApplicationName name) {
        // Apps are single-tenant for now
        return application;
    }
}
