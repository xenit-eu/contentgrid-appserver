package com.contentgrid.appserver.registry;

import com.contentgrid.appserver.application.model.Application;
import jakarta.servlet.http.HttpServletRequest;

public interface ApplicationResolver {
    Application resolve(HttpServletRequest request);
}
