package com.contentgrid.appserver.registry;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SingleApplicationResolver implements ApplicationResolver {

    @Getter
    private final Application application;

    @Override
    public Optional<Application> resolve(ApplicationName name) {
        // Apps are single-tenant for now
        if (name.equals(application.getName())) {
            return Optional.of(application);
        } else {
            return Optional.empty();
        }
    }
}
