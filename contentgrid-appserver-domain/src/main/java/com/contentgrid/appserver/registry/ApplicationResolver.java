package com.contentgrid.appserver.registry;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import jakarta.servlet.http.HttpServletRequest;

public interface ApplicationResolver {
    Application resolve(ApplicationName name);
}
