package com.contentgrid.appserver.domain.automations;

import com.contentgrid.appserver.application.model.Application;
import java.util.Optional;

public interface AutomationsModelResolver {

    Optional<AutomationsModel> resolve(Application application);

}
