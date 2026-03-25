package com.contentgrid.appserver.automations.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.automations.rest.AutomationsModel.AutomationAnnotationModel;
import com.contentgrid.appserver.automations.rest.AutomationsModel.AutomationModel;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

public class AutomationsModelTest {

    private static final String AUTOMATION_ID = UUID.randomUUID().toString();
    private static final String SYSTEM_ID = "my-system";
    private static final Map<String, Object> AUTOMATION_DATA = Map.of("foo", "bar");
    private static final String ENTITY_ANNOTATION_ID = UUID.randomUUID().toString();
    private static final Map<String, String> ENTITY_ANNOTATION_SUBJECT = Map.of("type", "entity", "entity", "invoice");
    private static final Map<String, Object> ENTITY_ANNOTATION_DATA = Map.of("color", "blue");
    private static final String ATTRIBUTE_ANNOTATION_ID = UUID.randomUUID().toString();
    private static final Map<String, String> ATTRIBUTE_ANNOTATION_SUBJECT = Map.of("type", "attribute", "entity", "invoice", "attribute", "content");
    private static final Map<String, Object> ATTRIBUTE_ANNOTATION_DATA = Map.of("type", "input");
    private static final String ENTITY = "invoice";

    @Test
    void loadMissingConfig() {
        Resource missingResource = Mockito.mock(Resource.class);
        Mockito.when(missingResource.exists())
                .thenReturn(false);
        assertThat(AutomationsRestController.loadConfig(missingResource))
                .isEqualTo(AutomationsModel.builder()
                        .automations(List.of())
                        .build());
    }

    @Test
    void loadConfig() {
        assertThat(AutomationsRestController.loadConfig(new ByteArrayResource("""
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
                            "entity": "invoice",
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
                            "entity": "invoice",
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
                .isEqualTo(AutomationsModel.builder()
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
                                                        .entity(ENTITY)
                                                        .data(ENTITY_ANNOTATION_DATA)
                                                        .build(),
                                                AutomationAnnotationModel.builder()
                                                        .id(ATTRIBUTE_ANNOTATION_ID)
                                                        .subject(ATTRIBUTE_ANNOTATION_SUBJECT)
                                                        .entity(ENTITY)
                                                        .data(ATTRIBUTE_ANNOTATION_DATA)
                                                        .build()
                                        ))
                                        .build()
                        ))
                        .build());
    }
}
