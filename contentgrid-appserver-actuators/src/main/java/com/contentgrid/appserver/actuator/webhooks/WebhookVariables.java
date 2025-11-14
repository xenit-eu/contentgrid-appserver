package com.contentgrid.appserver.actuator.webhooks;

import com.contentgrid.appserver.actuator.ContentgridApplicationProperties.SystemProperties;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import org.springframework.util.PropertyPlaceholderHelper.PlaceholderResolver;

@Value
@Builder
public class WebhookVariables implements PlaceholderResolver {
    SystemProperties systemProperties;
    Map<String, String> userVariables;

    @Override
    public String resolvePlaceholder(String placeholderName) {
        if (placeholderName.startsWith("vars.")) {
            return userVariables.get(placeholderName.substring("vars.".length()));
        }
        return switch (placeholderName) {
            case "system.application.id" -> systemProperties.getApplicationId();
            case "system.deployment.id" -> systemProperties.getDeploymentId();
            default -> throw new IllegalArgumentException(
                    String.format("Can not find a replacement for placeholder '%s'", placeholderName));
        };
    }
}
