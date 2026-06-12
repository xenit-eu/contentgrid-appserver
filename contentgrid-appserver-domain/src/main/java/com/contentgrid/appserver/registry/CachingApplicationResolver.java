package com.contentgrid.appserver.registry;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CachingApplicationResolver implements ApplicationResolver {

    private final ApplicationResolver delegate;
    private final Map<ApplicationName, Application> applications = new HashMap<>();

    @Override
    public Application resolve(ApplicationName name) {
        return applications.computeIfAbsent(name, delegate::resolve);
    }
}
