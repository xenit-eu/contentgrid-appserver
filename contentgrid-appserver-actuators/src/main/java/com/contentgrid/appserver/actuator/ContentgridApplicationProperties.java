package com.contentgrid.appserver.actuator;

import java.util.Map;
import lombok.Data;

@Data
public class ContentgridApplicationProperties {
    private SystemProperties system = new SystemProperties();
    private Map<String, String> variables;

    @Data
    public static class SystemProperties {
        private String deploymentId;
        private String applicationId;
        private String policyPackage;
    }
}
