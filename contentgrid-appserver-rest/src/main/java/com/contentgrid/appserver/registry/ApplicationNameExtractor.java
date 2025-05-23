package com.contentgrid.appserver.registry;

import com.contentgrid.appserver.application.model.values.ApplicationName;
import jakarta.servlet.http.HttpServletRequest;

public interface ApplicationNameExtractor {
    ApplicationName extract(HttpServletRequest request);
}
