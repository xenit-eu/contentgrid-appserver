package com.contentgrid.appserver.registry;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.ApplicationName;

public interface ApplicationResolver {
    Application resolve(ApplicationName name);
}
