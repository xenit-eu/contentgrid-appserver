package com.contentgrid.appserver.domain.automations;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

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

    public static AutomationsModel fromConfig(Resource resource) {
        if (resource.exists()) {
            try {
                @NonNull JsonMapper objectMapper = JsonMapper.builder()
                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .build();
                return objectMapper.readValue(resource.getInputStream(), AutomationsModel.class);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        } else {
            return AutomationsModel.builder().automations(List.of()).build();
        }
    }
}
