package com.contentgrid.appserver.actuator.webhooks;

import com.contentgrid.appserver.infrastructure.api.ArtifactEntry;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryUnreadableException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.util.PropertyPlaceholderHelper;
import org.springframework.util.SystemPropertyUtils;

@WebEndpoint(id = "webhooks")
@RequiredArgsConstructor
public class WebhookConfigActuator {
    private final ArtifactEntry artifactEntry;
    private final WebhookVariables webhookVariables;
    private static final PropertyPlaceholderHelper PROPERTY_PLACEHOLDER_HELPER = new PropertyPlaceholderHelper(
            SystemPropertyUtils.PLACEHOLDER_PREFIX,
            SystemPropertyUtils.PLACEHOLDER_SUFFIX
    );

    @ReadOperation(producesFrom = WebhookConfigProducible.class)
    public String getConfig() throws IOException, ArtifactEntryUnreadableException {
        if (artifactEntry != null) {
            String contents = readContents(artifactEntry);
            return PROPERTY_PLACEHOLDER_HELPER.replacePlaceholders(contents, webhookVariables);
        }
        throw new FileNotFoundException("webhook config file is not present");
    }

    static String readContents(ArtifactEntry artifactEntry) throws IOException, ArtifactEntryUnreadableException {
        try (InputStream resourceStream = artifactEntry.getInputStream()) {
            return new String(resourceStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
