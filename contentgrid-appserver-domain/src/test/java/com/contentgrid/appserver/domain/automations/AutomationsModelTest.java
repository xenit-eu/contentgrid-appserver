package com.contentgrid.appserver.domain.automations;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.domain.automations.AutomationsModel.AutomationAnnotationModel;
import com.contentgrid.appserver.domain.automations.AutomationsModel.AutomationModel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

class AutomationsModelTest {

    private static final String AUTOMATION_ID = "a0da9322-3dae-4b5a-8f92-c3cb53989938";
    private static final String SYSTEM_ID = "my-system";
    private static final Map<String, Object> AUTOMATION_DATA = Map.of("foo", "bar");
    private static final String ENTITY_ANNOTATION_ID = "92aeb778-e2b8-42c1-8c3b-513d18ff01cb";
    private static final Map<String, String> ENTITY_ANNOTATION_SUBJECT = Map.of("type", "entity", "entity", "invoice");
    private static final Map<String, Object> ENTITY_ANNOTATION_DATA = Map.of("color", "blue");
    private static final String ATTRIBUTE_ANNOTATION_ID = "4fb6993d-a163-4746-a1aa-4a0018a796d4";
    private static final Map<String, String> ATTRIBUTE_ANNOTATION_SUBJECT = Map.of("type", "attribute", "entity", "invoice", "attribute", "content");
    private static final Map<String, Object> ATTRIBUTE_ANNOTATION_DATA = Map.of("type", "input");

    private static final AutomationsModel MODEL = AutomationsModel.builder()
            .automations(List.of(
                    AutomationModel.builder()
                            .id(AUTOMATION_ID)
                            .system(SYSTEM_ID)
                            .name("my-automation")
                            .data(AUTOMATION_DATA)
                            .annotations(List.of(
                                    AutomationAnnotationModel.builder()
                                            .id(ENTITY_ANNOTATION_ID)
                                            .subject(ENTITY_ANNOTATION_SUBJECT)
                                            .data(ENTITY_ANNOTATION_DATA)
                                            .build(),
                                    AutomationAnnotationModel.builder()
                                            .id(ATTRIBUTE_ANNOTATION_ID)
                                            .subject(ATTRIBUTE_ANNOTATION_SUBJECT)
                                            .data(ATTRIBUTE_ANNOTATION_DATA)
                                            .build()
                            ))
                            .build()
            ))
            .build();

    private final ResourceLoader resourceLoader = new DefaultResourceLoader(AutomationsModelTest.class.getClassLoader());

    @Test
    void loadConfigNotFound() {
        Resource missingResource = Mockito.mock(Resource.class);
        Mockito.when(missingResource.exists())
                .thenReturn(false);
        assertThat(AutomationsModel.fromConfig(missingResource))
                .isEqualTo(AutomationsModel.builder()
                        .automations(List.of())
                        .build());
    }

    @Test
    void loadConfig() {
        assertThat(AutomationsModel.fromConfig(new ByteArrayResource("""
                {
                    "automations": [ {
                        "id": "${AUTOMATION_ID}",
                        "system": "${SYSTEM_ID}",
                        "name": "my-automation",
                        "data": {
                            "foo": "bar"
                        },
                        "annotations": [ {
                            "id": "${ENTITY_ANNOTATION_ID}",
                            "subject": {
                                "type": "entity",
                                "entity": "invoice"
                            },
                            "data": {
                                "color": "blue"
                            }
                        },
                        {
                            "id": "${ATTRIBUTE_ANNOTATION_ID}",
                            "subject": {
                                "type": "attribute",
                                "entity": "invoice",
                                "attribute": "content"
                            },
                            "data": {
                                "type": "input"
                            }
                        } ]
                    } ]
                }
                """.replace("${AUTOMATION_ID}", AUTOMATION_ID)
                .replace("${SYSTEM_ID}", SYSTEM_ID)
                .replace("${ENTITY_ANNOTATION_ID}", ENTITY_ANNOTATION_ID)
                .replace("${ATTRIBUTE_ANNOTATION_ID}", ATTRIBUTE_ANNOTATION_ID).getBytes())))
                .isEqualTo(MODEL);
    }

    @Test
    void loadFileConfig() {
        var resource = resourceLoader.getResource("classpath:automations.json");
        assertThat(AutomationsModel.fromConfig(resource))
                .isEqualTo(MODEL);
    }
}
