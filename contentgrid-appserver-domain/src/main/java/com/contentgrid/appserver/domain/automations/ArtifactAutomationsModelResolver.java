package com.contentgrid.appserver.domain.automations;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.infrastructure.api.Artifact;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntry;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryUnreadableException;
import com.contentgrid.appserver.infrastructure.api.ArtifactException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ArtifactAutomationsModelResolver implements AutomationsModelResolver {

    private static final Path PATH = Path.of("automation", "automations.json");

    private final Artifact artifact;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Override
    public AutomationsModel resolve(Application application) {
        try {
            return artifact.load(PATH)
                    .map(this::readEntry)
                    .orElseGet(() -> AutomationsModel.builder().automations(List.of()).build());
        } catch (ArtifactException e) {
            throw new IllegalStateException(e);
        }
    }

    private AutomationsModel readEntry(ArtifactEntry artifactEntry) {
        try {
            return objectMapper.readValue(artifactEntry.getInputStream(), AutomationsModel.class);
        } catch (IOException | ArtifactEntryUnreadableException e) {
            throw new IllegalStateException(e);
        }
    }
}
