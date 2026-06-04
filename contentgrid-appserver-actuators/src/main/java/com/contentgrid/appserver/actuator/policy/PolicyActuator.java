package com.contentgrid.appserver.actuator.policy;

import com.contentgrid.appserver.infrastructure.api.BlueprintArtifact;
import com.contentgrid.appserver.infrastructure.api.BlueprintArtifactItem;
import com.contentgrid.appserver.infrastructure.api.BlueprintArtifactItemUnreadableException;
import com.contentgrid.appserver.infrastructure.api.BlueprintArtifactException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.util.PropertyPlaceholderHelper;
import org.springframework.util.SystemPropertyUtils;

@WebEndpoint(id = "policy")
@RequiredArgsConstructor
public class PolicyActuator {
    private static final Path PATH = Path.of("rego", "policy.rego");

    private final BlueprintArtifact blueprintArtifact;
    private final PolicyVariables policyVariables;
    private static final PropertyPlaceholderHelper PROPERTY_PLACEHOLDER_HELPER = new PropertyPlaceholderHelper(
            SystemPropertyUtils.PLACEHOLDER_PREFIX,
            SystemPropertyUtils.PLACEHOLDER_SUFFIX
    );

    @ReadOperation(producesFrom = RegoProducible.class)
    public String readPolicy() throws IOException, BlueprintArtifactException, BlueprintArtifactItemUnreadableException {
        var maybeBlueprintArtifactItem = blueprintArtifact.load(PATH);
        if (maybeBlueprintArtifactItem.isPresent()) {
            String contents = readContents(maybeBlueprintArtifactItem.get());
            return PROPERTY_PLACEHOLDER_HELPER.replacePlaceholders(contents, policyVariables);
        } else {
            throw new FileNotFoundException("rego file at " + PATH + " in " + blueprintArtifact.getReference() + " is not present");
        }
    }

    public String readContents(BlueprintArtifactItem blueprintArtifactItem) throws IOException, BlueprintArtifactItemUnreadableException {
        try(InputStream resourceStream = blueprintArtifactItem.getInputStream()) {
            return new String(resourceStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
