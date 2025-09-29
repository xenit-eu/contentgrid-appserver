package com.contentgrid.appserver.actuator.policy;

import lombok.Builder;
import lombok.Value;
import org.springframework.util.PropertyPlaceholderHelper.PlaceholderResolver;

@Value
@Builder
public class PolicyVariables implements PlaceholderResolver {
    String policyPackageName;

    @Override
    public String resolvePlaceholder(String placeholderName) {
        return switch (placeholderName) {
            case "system.policy.package" -> policyPackageName;
            default -> throw new IllegalArgumentException(
                    String.format("Can not find a replacement for placeholder '%s'", placeholderName));
        };
    }
}
