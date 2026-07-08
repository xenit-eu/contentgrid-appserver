package com.contentgrid.appserver.actuator.policy;

import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactException;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactItem;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactItemUnreadableException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.jspecify.annotations.Nullable;
import org.springframework.util.PropertyPlaceholderHelper;
import org.springframework.util.StringUtils;
import org.springframework.util.SystemPropertyUtils;

@WebEndpoint(id = "policy")
@Slf4j
@RequiredArgsConstructor
public class PolicyActuator {
    private static final Path PATH = Path.of("rego", "policy.rego");
    private static final PropertyPlaceholderHelper PROPERTY_PLACEHOLDER_HELPER = new PropertyPlaceholderHelper(
            SystemPropertyUtils.PLACEHOLDER_PREFIX,
            SystemPropertyUtils.PLACEHOLDER_SUFFIX
    );

    private final BlueprintArtifact blueprintArtifact;
    @Nullable
    private final String policyPackage;

    @ReadOperation(producesFrom = RegoProducible.class)
    public String readPolicy() throws IOException, BlueprintArtifactException, BlueprintArtifactItemUnreadableException {
        if (!StringUtils.hasText(policyPackage)) {
            log.info("Skipping reading the rego policy because the policy package is not set.");
            return null;
        }
        var maybeBlueprintArtifactItem = blueprintArtifact.load(PATH);
        if (maybeBlueprintArtifactItem.isEmpty()) {
            throw new FileNotFoundException("rego file at " + PATH + " in " + blueprintArtifact.getReference() + " is not present");
        }
        return PROPERTY_PLACEHOLDER_HELPER.replacePlaceholders(readContents(maybeBlueprintArtifactItem.get()), new PolicyVariables(policyPackage));
    }

    public String readContents(BlueprintArtifactItem blueprintArtifactItem) throws IOException, BlueprintArtifactItemUnreadableException {
        try(InputStream resourceStream = blueprintArtifactItem.getInputStream()) {
            return new String(resourceStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
