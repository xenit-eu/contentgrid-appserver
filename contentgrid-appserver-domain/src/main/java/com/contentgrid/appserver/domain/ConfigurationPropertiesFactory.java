package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.application.model.Application;

public interface ConfigurationPropertiesFactory {
    ConfigurationProperties createConfigurationProperties(Application application);
}
