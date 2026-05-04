package com.contentgrid.appserver.registry;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import java.util.Optional;

public interface ApplicationResolver {
    Optional<Application> resolve(ApplicationName name);
}
