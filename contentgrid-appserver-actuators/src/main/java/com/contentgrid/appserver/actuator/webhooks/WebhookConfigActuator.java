package com.contentgrid.appserver.actuator.webhooks;

import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactItem;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactItemUnreadableException;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactException;
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

@WebEndpoint(id = "webhooks")
@RequiredArgsConstructor
public class WebhookConfigActuator {
    private static final Path PATH = Path.of("eventhandler", "webhooks.json");

    private final BlueprintArtifact blueprintArtifact;
    private final WebhookVariables webhookVariables;
    private static final PropertyPlaceholderHelper PROPERTY_PLACEHOLDER_HELPER = new PropertyPlaceholderHelper(
            SystemPropertyUtils.PLACEHOLDER_PREFIX,
            SystemPropertyUtils.PLACEHOLDER_SUFFIX
    );

    @ReadOperation(producesFrom = WebhookConfigProducible.class)
    public String getConfig() throws IOException, BlueprintArtifactException, BlueprintArtifactItemUnreadableException {
        var maybeBlueprintArtifactItem = blueprintArtifact.load(PATH);
        if (maybeBlueprintArtifactItem.isPresent()) {
            String contents = readContents(maybeBlueprintArtifactItem.get());
            return PROPERTY_PLACEHOLDER_HELPER.replacePlaceholders(contents, webhookVariables);
        } else {
            throw new FileNotFoundException("rego file at " + PATH + " in " + blueprintArtifact.getReference() + " is not present");
        }
    }

    static String readContents(BlueprintArtifactItem blueprintArtifactItem) throws IOException, BlueprintArtifactItemUnreadableException {
        try (InputStream resourceStream = blueprintArtifactItem.getInputStream()) {
            return new String(resourceStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
