package com.contentgrid.appserver.automations.rest;

import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.APPLICATION;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contentgrid.appserver.automations.rest.AutomationsModel.AutomationAnnotationModel;
import com.contentgrid.appserver.automations.rest.AutomationsModel.AutomationModel;
import com.contentgrid.appserver.automations.rest.AutomationsRestControllerTest.TestConfig;
import com.contentgrid.appserver.example.ContentgridApp;
import com.contentgrid.appserver.registry.SingleApplicationResolver;
import com.contentgrid.appserver.rest.test.WithMockJwt;
import com.contentgrid.thunx.encoding.json.JsonThunkExpressionCoder;
import com.contentgrid.thunx.predicates.model.Comparison;
import com.contentgrid.thunx.predicates.model.Scalar;
import com.contentgrid.thunx.predicates.model.SymbolicReference;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = {ContentgridApp.class, TestConfig.class}, properties = {
        "contentgrid.thunx.abac.source=header",
        "contentgrid.appserver.content-store.type=ephemeral",
        "contentgrid.events.rabbitmq.enabled=false",
})
@AutoConfigureMockMvc
@WithMockJwt
class AutomationsRestControllerTest {

    private static final String AUTOMATION_1_ID = UUID.randomUUID().toString();
    private static final String SYSTEM_1_ID = "my-system";
    private static final Map<String, Object> AUTOMATION_DATA = Map.of("foo", "bar");
    private static final String AUTOMATION_2_ID = UUID.randomUUID().toString();
    private static final String SYSTEM_2_ID = "other-system";
    private static final String ENTITY_ANNOTATION_ID = UUID.randomUUID().toString();
    private static final Map<String, String> ENTITY_ANNOTATION_SUBJECT = Map.of("type", "entity", "entity", "invoice");
    private static final Map<String, Object> ENTITY_ANNOTATION_DATA = Map.of("color", "blue");
    private static final String ATTRIBUTE_ANNOTATION_ID = UUID.randomUUID().toString();
    private static final Map<String, String> ATTRIBUTE_ANNOTATION_SUBJECT = Map.of("type", "attribute", "entity", "invoice", "attribute", "content");
    private static final Map<String, Object> ATTRIBUTE_ANNOTATION_DATA = Map.of("type", "input");
    private static final String RELATION_ANNOTATION_ID = UUID.randomUUID().toString();
    private static final Map<String, String> RELATION_ANNOTATION_SUBJECT = Map.of("type", "relation", "entity", "invoice", "relation", "products");
    private static final Map<String, Object> RELATION_ANNOTATION_DATA = Map.of("type", "output");
    private static final String ENTITY = "invoice";

    // true = true
    private static final Comparison DEFAULT_POLICY = Comparison.areEqual(Scalar.of(true), Scalar.of(true));

    // automation.system = my-system
    private static final Comparison MY_SYSTEM_POLICY = Comparison.areEqual(
            SymbolicReference.of("entity", path -> path.string("system")),
            Scalar.of("my-system"));

    private static String headerEncode(ThunkExpression<Boolean> expression) {
        var bytes = new JsonThunkExpressionCoder().encode(expression);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AutomationsRestController controller;

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        public SingleApplicationResolver singleApplicationResolver() {
            return new SingleApplicationResolver(APPLICATION);
        }
    }

    @BeforeEach
    void setup() {
        controller.setModel(AutomationsModel.builder()
                .automations(List.of(
                        AutomationModel.builder()
                                .id(AUTOMATION_1_ID)
                                .system(SYSTEM_1_ID)
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
                                .build(),
                        AutomationModel.builder()
                                .id(AUTOMATION_2_ID)
                                .system(SYSTEM_2_ID)
                                .name("other-automation")
                                .data(Map.of())
                                .annotations(List.of(
                                        AutomationAnnotationModel.builder()
                                                .id(RELATION_ANNOTATION_ID)
                                                .subject(RELATION_ANNOTATION_SUBJECT)
                                                .entity(ENTITY)
                                                .data(RELATION_ANNOTATION_DATA)
                                                .build()
                                ))
                                .build()
                ))
                .build());
    }

    @Test
    void getRoot_containsLink() throws Exception {
        mockMvc.perform(get("/").accept(MediaType.APPLICATION_JSON)
                        .header("X-ABAC-Context", headerEncode(DEFAULT_POLICY))
                )
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                            _links: {
                                "automation:registrations": {
                                    href: "http://localhost/.contentgrid/automations"
                                }
                            }
                        }
                        """));
    }

    @Test
    void getAutomations_http200() throws Exception {
        mockMvc.perform(get("/.contentgrid/automations")
                        .header("X-ABAC-Context", headerEncode(DEFAULT_POLICY)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                            _embedded: {
                                item: [ {
                                    id: "${AUTOMATION_1_ID}",
                                    system: "${SYSTEM_1_ID}",
                                    name: "my-automation",
                                    _links: {
                                        self: { href: "http://localhost/.contentgrid/automations/${AUTOMATION_1_ID}" }
                                    }
                                }, {
                                    id: "${AUTOMATION_2_ID}",
                                    system: "${SYSTEM_2_ID}",
                                    name: "other-automation",
                                    _links: {
                                        self: { href: "http://localhost/.contentgrid/automations/${AUTOMATION_2_ID}" }
                                    }
                                } ]
                            },
                            _links: {
                                self: { href: "http://localhost/.contentgrid/automations" }
                            }
                        }
                        """.replace("${AUTOMATION_1_ID}", AUTOMATION_1_ID)
                        .replace("${SYSTEM_1_ID}", SYSTEM_1_ID)
                        .replace("${AUTOMATION_2_ID}", AUTOMATION_2_ID)
                        .replace("${SYSTEM_2_ID}", SYSTEM_2_ID)))
                .andExpect(jsonPath("$._embedded.item[0].data").doesNotExist());
    }

    @Test
    void getAutomations_withPolicy_http200() throws Exception {
        mockMvc.perform(get("/.contentgrid/automations")
                        .header("X-ABAC-Context", headerEncode(MY_SYSTEM_POLICY)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                            {
                                _embedded: {
                                    item: [ {
                                        id: "${AUTOMATION_1_ID}",
                                        system: "${SYSTEM_1_ID}",
                                        name: "my-automation",
                                        _links: {
                                            self: { href: "http://localhost/.contentgrid/automations/${AUTOMATION_1_ID}" }
                                        }
                                    } ]
                                },
                                _links: {
                                    self: { href: "http://localhost/.contentgrid/automations" }
                                }
                            }
                            """.replace("${AUTOMATION_1_ID}", AUTOMATION_1_ID)
                        .replace("${SYSTEM_1_ID}", SYSTEM_1_ID)))
                .andExpect(jsonPath("$._embedded.item[0].data").doesNotExist())
                .andExpect(jsonPath("$._embedded.item", hasSize(1)));
    }

    @Test
    void getAutomation_http200() throws Exception {
        mockMvc.perform(get("/.contentgrid/automations/{id}", AUTOMATION_1_ID)
                        .header("X-ABAC-Context", headerEncode(DEFAULT_POLICY)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                            id: "${AUTOMATION_1_ID}",
                            system: "${SYSTEM_1_ID}",
                            name: "my-automation",
                            data: {
                                foo: "bar"
                            },
                            _embedded: {
                                "automation:annotation": [ {
                                    id: "${ENTITY_ANNOTATION_ID}",
                                    subject: {
                                        type: "entity",
                                        entity: "invoice"
                                    },
                                    data: {
                                        color: "blue"
                                    },
                                    _links: {
                                        "automation:target-entity": {
                                            href: "http://localhost/profile/invoices"
                                        }
                                    }
                                },
                                {
                                    id: "${ATTRIBUTE_ANNOTATION_ID}",
                                    subject: {
                                        type: "attribute",
                                        entity: "invoice",
                                        attribute: "content"
                                    },
                                    data: {
                                        type: "input"
                                    },
                                    _links: {
                                        "automation:target-entity": {
                                            href: "http://localhost/profile/invoices"
                                        }
                                    }
                                } ]
                            },
                            _links: {
                                self: { href: "http://localhost/.contentgrid/automations/${AUTOMATION_1_ID}" }
                            }
                        }
                        """.replace("${AUTOMATION_1_ID}", AUTOMATION_1_ID)
                        .replace("${SYSTEM_1_ID}", SYSTEM_1_ID)
                        .replace("${ENTITY_ANNOTATION_ID}", ENTITY_ANNOTATION_ID)
                        .replace("${ATTRIBUTE_ANNOTATION_ID}", ATTRIBUTE_ANNOTATION_ID)));
    }

    @Test
    void getAutomation_wrongId_http404() throws Exception {
        mockMvc.perform(get("/.contentgrid/automations/{id}", UUID.randomUUID().toString())
                        .header("X-ABAC-Context", headerEncode(DEFAULT_POLICY)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAutomation_noAccess_http404() throws Exception {
        mockMvc.perform(get("/.contentgrid/automations/{id}", AUTOMATION_2_ID)
                        .header("X-ABAC-Context", headerEncode(MY_SYSTEM_POLICY)))
                .andExpect(status().isNotFound());
    }

}
