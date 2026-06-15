package com.contentgrid.appserver.domain.automations;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactItem;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactItemUnreadableException;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BlueprintArtifactAutomationsModelResolver implements AutomationsModelResolver {

    private static final Path PATH = Path.of("automation", "automations.json");

    private final BlueprintArtifact blueprintArtifact;
    private static final JsonMapper OBJECT_MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

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
            return OBJECT_MAPPER.readValue(blueprintArtifactItem.getInputStream(), AutomationsModel.class);
        } catch (BlueprintArtifactItemUnreadableException e) {
            throw new IllegalStateException(e);
        }
    }
}
