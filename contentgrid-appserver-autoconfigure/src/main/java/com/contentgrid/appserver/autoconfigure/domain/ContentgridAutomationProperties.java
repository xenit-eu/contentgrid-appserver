package com.contentgrid.appserver.autoconfigure.domain;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.Data;

@Data
public class ContentgridAutomationProperties {
    private Map<String, AutomationRegistration> registration = new HashMap<>();

    public Optional<AutomationRegistration> getRegistration(String extensionName) {
        return Optional.ofNullable(registration.get(extensionName));
    }

    @Data
    public static class AutomationRegistration {
        private Map<String, URI> basePath = new HashMap<>();

        public Optional<URI> getBasePath(String prefixName) {
            return Optional.ofNullable(basePath.get(prefixName));
        }
    }
}
