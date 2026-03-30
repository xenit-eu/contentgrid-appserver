package com.contentgrid.appserver.domain.automations;

import com.contentgrid.appserver.application.model.Application;

public interface AutomationsModelResolver {

    AutomationsModel resolve(Application application);

}
