package com.contentgrid.appserver.domain;

import java.net.URI;
import java.util.Optional;

public interface ConfigurationProperties {
    String getApplicationId();
    Optional<URI> getAutomationSystemBaseUrl(String automationSystemId, String basePathName);
}
