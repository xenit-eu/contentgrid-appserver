package com.contentgrid.appserver.registry;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ApplicationResolverRegistry implements ApplicationResolver {

    private final List<ApplicationResolver> resolvers;
    private final Map<ApplicationName, Application> applications = new HashMap<>();

    @Override
    public Optional<Application> resolve(ApplicationName name) {
        return Optional.ofNullable(applications.computeIfAbsent(name, key -> {
            for (var resolver : resolvers) {
                var application = resolver.resolve(key);
                if (application.isPresent()) {
                    return application.get();
                }
            }
            return null;
        }));
    }
}
