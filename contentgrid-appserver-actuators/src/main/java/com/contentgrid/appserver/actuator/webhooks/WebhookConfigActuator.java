package com.contentgrid.appserver.actuator.webhooks;

import com.contentgrid.appserver.infrastructure.api.Artifact;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryUnreadableException;
import com.contentgrid.appserver.infrastructure.api.ArtifactException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.util.PropertyPlaceholderHelper;
import org.springframework.util.SystemPropertyUtils;

@WebEndpoint(id = "webhooks")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class WebhookConfigActuator {
    private static final Path PATH = Path.of("eventhandler", "webhooks.json");
    private static final PropertyPlaceholderHelper PROPERTY_PLACEHOLDER_HELPER = new PropertyPlaceholderHelper(
            SystemPropertyUtils.PLACEHOLDER_PREFIX,
            SystemPropertyUtils.PLACEHOLDER_SUFFIX
    );

    private final String content;

    public static WebhookConfigActuator fromArtifact(Artifact artifact, WebhookVariables webhookVariables)
            throws ArtifactException, IOException, ArtifactEntryUnreadableException {
        var entry = artifact.load(PATH);
        if (entry.isEmpty()) {
            return new WebhookConfigActuator(null);
        }
        try (InputStream is = entry.get().getInputStream()) {
            var rawContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return new WebhookConfigActuator(PROPERTY_PLACEHOLDER_HELPER.replacePlaceholders(rawContent, webhookVariables));
        }
    }

    @ReadOperation(producesFrom = WebhookConfigProducible.class)
    public String getConfig() throws FileNotFoundException {
        if (content == null) {
            throw new FileNotFoundException("webhooks file at " + PATH + " is not present");
        }
        return content;
    }
}
