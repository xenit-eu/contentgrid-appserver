package com.contentgrid.appserver.domain.automations;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AutomationsModelResolverRegistry implements AutomationsModelResolver {

    private final List<AutomationsModelResolver> resolvers;
    private final Map<ApplicationName, AutomationsModel> automationsModels = new HashMap<>();

    @Override
    public Optional<AutomationsModel> resolve(Application application) {
        return Optional.of(automationsModels.computeIfAbsent(application.getName(), unused -> {
            for (var resolver : resolvers) {
                var model = resolver.resolve(application);
                if (model.isPresent()) {
                    return model.get();
                }
            }
            // Return an empty model by default
            return AutomationsModel.builder().automations(List.of()).build();
        }));
    }
}
