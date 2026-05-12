package com.contentgrid.appserver.domain.automations;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CachingAutomationsModelResolver implements AutomationsModelResolver {

    private final AutomationsModelResolver delegate;
    private final Map<ApplicationName, AutomationsModel> automationsModels = new HashMap<>();

    @Override
    public AutomationsModel resolve(Application application) {
        return automationsModels.computeIfAbsent(application.getName(), name -> delegate.resolve(application));
    }
}
