package com.contentgrid.appserver.automations.model;

import com.contentgrid.appserver.application.model.Application;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SingleAutomationsModelResolver implements AutomationsModelResolver {

    private final AutomationsModel model;

    @Override
    public AutomationsModel resolve(Application application) {
        return model;
    }
}
