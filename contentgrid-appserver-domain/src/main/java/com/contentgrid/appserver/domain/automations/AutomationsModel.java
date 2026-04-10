package com.contentgrid.appserver.domain.automations;

import com.contentgrid.appserver.infrastructure.api.ArtifactEntry;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryUnreadableException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.springframework.core.io.Resource;

@Value
@Builder
@Jacksonized
public class AutomationsModel {

    @NonNull List<AutomationModel> automations;

    @Value
    @Builder
    @Jacksonized
    public static class AutomationModel {

        @NonNull String id;
        @NonNull String system;
        @NonNull String name;
        @NonNull Map<String, Object> data;
        @NonNull List<AutomationAnnotationModel> annotations;
    }

    @Value
    @Builder
    @Jacksonized
    public static class AutomationAnnotationModel {

        @NonNull String id;
        @NonNull Map<String, String> subject;
        @NonNull Map<String, Object> data;
    }

    public static AutomationsModel fromConfig(ArtifactEntry artifactEntry) {
        if (artifactEntry != null) {
            try {
                @NonNull ObjectMapper objectMapper = new ObjectMapper()
                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
                return objectMapper.readValue(artifactEntry.getInputStream(), AutomationsModel.class);
            } catch (IOException | ArtifactEntryUnreadableException e) {
                throw new IllegalStateException(e);
            }
        } else {
            return AutomationsModel.builder().automations(List.of()).build();
        }
    }
}
