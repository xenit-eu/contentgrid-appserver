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
import org.springframework.util.PropertyPlaceholderHelper;
import org.springframework.util.SystemPropertyUtils;

/**
 * Reads the rego policy from the blueprint artifact and resolves its placeholders.
 */
@RequiredArgsConstructor
class RegoPolicyLoader {

    private static final Path PATH = Path.of("rego", "policy.rego");
    private static final PropertyPlaceholderHelper PROPERTY_PLACEHOLDER_HELPER = new PropertyPlaceholderHelper(
            SystemPropertyUtils.PLACEHOLDER_PREFIX,
            SystemPropertyUtils.PLACEHOLDER_SUFFIX
    );

    private final BlueprintArtifact blueprintArtifact;

    String readPolicy(String policyPackage)
            throws IOException, BlueprintArtifactException, BlueprintArtifactItemUnreadableException {
        var maybeBlueprintArtifactItem = blueprintArtifact.load(PATH);
        if (maybeBlueprintArtifactItem.isEmpty()) {
            throw new FileNotFoundException(
                    "rego file at " + PATH + " in " + blueprintArtifact.getReference() + " is not present");
        }
        return PROPERTY_PLACEHOLDER_HELPER.replacePlaceholders(readContents(maybeBlueprintArtifactItem.get()),
                new PolicyVariables(policyPackage));
    }

    private String readContents(BlueprintArtifactItem blueprintArtifactItem)
            throws IOException, BlueprintArtifactItemUnreadableException {
        try (InputStream resourceStream = blueprintArtifactItem.getInputStream()) {
            return new String(resourceStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
