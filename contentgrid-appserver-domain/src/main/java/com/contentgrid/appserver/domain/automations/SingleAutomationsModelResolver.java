package com.contentgrid.appserver.domain.automations;

import com.contentgrid.appserver.application.model.Application;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SingleAutomationsModelResolver implements AutomationsModelResolver {

    private final AutomationsModel model;

    @Override
    public Optional<AutomationsModel> resolve(Application application) {
        return Optional.of(model);
    }
}
