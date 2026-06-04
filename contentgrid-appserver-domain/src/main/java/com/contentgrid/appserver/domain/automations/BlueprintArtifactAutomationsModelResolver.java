package com.contentgrid.appserver.domain.automations;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.infrastructure.api.BlueprintArtifact;
import com.contentgrid.appserver.infrastructure.api.BlueprintArtifactItem;
import com.contentgrid.appserver.infrastructure.api.BlueprintArtifactItemUnreadableException;
import com.contentgrid.appserver.infrastructure.api.BlueprintArtifactException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BlueprintArtifactAutomationsModelResolver implements AutomationsModelResolver {

    private static final Path PATH = Path.of("automation", "automations.json");

    private final BlueprintArtifact blueprintArtifact;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Override
    public AutomationsModel resolve(Application application) {
        try {
            return blueprintArtifact.load(PATH)
                    .map(this::readItem)
                    .orElseGet(() -> AutomationsModel.builder().automations(List.of()).build());
        } catch (BlueprintArtifactException e) {
            throw new IllegalStateException(e);
        }
    }

    private AutomationsModel readItem(BlueprintArtifactItem blueprintArtifactItem) {
        try {
            return objectMapper.readValue(blueprintArtifactItem.getInputStream(), AutomationsModel.class);
        } catch (IOException | BlueprintArtifactItemUnreadableException e) {
            throw new IllegalStateException(e);
        }
    }
}
