package com.contentgrid.appserver.automations.model;

import com.contentgrid.appserver.application.model.Application;

public interface AutomationsModelResolver {

    AutomationsModel resolve(Application application);

}
