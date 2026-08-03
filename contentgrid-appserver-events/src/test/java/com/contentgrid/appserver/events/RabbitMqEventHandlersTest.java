package com.contentgrid.appserver.events;

import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.APPLICATION;
import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.PRODUCT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.contentgrid.appserver.domain.DatamodelApi;
import com.contentgrid.appserver.domain.authorization.AuthorizationContext;
import com.contentgrid.appserver.domain.data.MapRequestInputData;
import com.contentgrid.appserver.rest.test.TestApplication;
import com.contentgrid.appserver.query.engine.api.TableCreator;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;


@SpringBootTest(classes = TestApplication.class, properties = {
        "contentgrid.events.rabbitmq.enabled=true",
        "contentgrid.system.policyPackage=policy-package-123",
        "spring.rabbitmq.host=foo",
        "contentgrid.system.deployment-id="+RabbitMqEventHandlersTest.DEPLOYMENT_ID,
        "contentgrid.system.application-id="+RabbitMqEventHandlersTest.APP_ID,
        "contentgrid.events.webhook-config-url="+RabbitMqEventHandlersTest.CONFIG_URL
})
@AutoConfigureMockMvc
class RabbitMqEventHandlersTest {

    public static final String APP_ID = "fb84de64-faae-11f0-b1cb-1be78580b649";
    public static final String DEPLOYMENT_ID = "0b682b92-faaf-11f0-b95e-376e533e6c92";
    public static final String CONFIG_URL = "http://localhost:8080";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatamodelApi datamodelApi;

    @Autowired
    TableCreator tableCreator;

    @BeforeEach
    void setup() {
        tableCreator.createTables(APPLICATION);
    }

    @AfterEach
    void teardown() {
        tableCreator.dropTables(APPLICATION);

    }

    @MockitoBean
    RabbitTemplate rabbitTemplate;

    @Test
    void testCreateEvent() throws Exception {

        var data = new MapRequestInputData(Map.of(
                "name", "Widget Reprogrammer",
                "description", "Flashes firmware to widgets",
                "price", 299.99
        ));

        var created = datamodelApi.create(APPLICATION, PRODUCT.getName(), data, AuthorizationContext.allowAll()).getIdentity();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate, timeout(1000)).send(any(), messageCaptor.capture());

        var message = messageCaptor.getValue();
        var headers = message.getMessageProperties().getHeaders();
        assertThat(headers).containsEntry("trigger", "create");
        assertThat(headers).containsEntry("entity", PRODUCT.getName().getValue());

        // Used by slingshot for reading the message
        assertThat(message.getMessageProperties().getContentType()).isEqualTo("text/plain");
        assertThat(message.getMessageProperties().getContentEncoding()).isEqualTo("UTF-8");
        // Used by slingshot for constructing the outgoing request
        assertThat(headers).containsEntry("application_id", APP_ID);
        assertThat(headers).containsEntry("deployment_id", DEPLOYMENT_ID);
        // Used by slingshot for fetching the target URLs
        assertThat(headers).containsEntry("webhookConfigUrl", CONFIG_URL);

        var mapper = new ObjectMapper();
        var expected = mapper.readTree(CREATED.replaceAll("<id>", created.getEntityId().getValue().toString()));
        var actual = mapper.readTree(message.getBody());
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void testDeleteEvent() throws Exception {

        var data = new MapRequestInputData(Map.of(
                "name", "Widget Reprogrammer",
                "description", "Flashes firmware to widgets",
                "price", 299.99
        ));

        var created = datamodelApi.create(APPLICATION, PRODUCT.getName(), data, AuthorizationContext.allowAll()).getIdentity();

        datamodelApi.deleteEntity(APPLICATION, created.toRequest(), AuthorizationContext.allowAll());

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate, atLeast(2)).send(any(), messageCaptor.capture());

        var messages = messageCaptor.getAllValues();
        assertThat(messages).anySatisfy(message -> {
            var headers = message.getMessageProperties().getHeaders();
            assertThat(headers).containsEntry("trigger", "delete");
            assertThat(headers).containsEntry("entity", PRODUCT.getName().getValue());

            var mapper = new ObjectMapper();
            var expected = mapper.readTree(DELETED.replaceAll("<id>", created.getEntityId().getValue().toString()));
            var actual = mapper.readTree(message.getBody());
            assertThat(actual).isEqualTo(expected);
        });
    }

    @Test
    void testUpdateEvent() throws Exception {

        var data = new MapRequestInputData(Map.of(
                "name", "Widget Reprogrammer",
                "description", "Flashes firmware to widgets",
                "price", 299.99
        ));

        var created = datamodelApi.create(APPLICATION, PRODUCT.getName(), data, AuthorizationContext.allowAll()).getIdentity();

        var updateData = new MapRequestInputData(Map.of("price", 300.00));
        datamodelApi.updatePartial(APPLICATION, created.toRequest(), updateData, AuthorizationContext.allowAll());

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate, atLeast(2)).send(any(), messageCaptor.capture());

        var messages = messageCaptor.getAllValues();
        assertThat(messages).anySatisfy(message -> {
            var headers = message.getMessageProperties().getHeaders();
            assertThat(headers).containsEntry("trigger", "update");
            assertThat(headers).containsEntry("entity", PRODUCT.getName().getValue());

            var mapper = new ObjectMapper();
            var expected = mapper.readTree(UPDATED.replaceAll("<id>", created.getEntityId().getValue().toString()));
            var actual = mapper.readTree(message.getBody());
            assertThat(actual).isEqualTo(expected);
        });
    }

    private static final String CREATED = """
                {
                  "trigger": "create",
                  "old": null,
                  "new": {
                    "id": "<id>",
                    "name": "Widget Reprogrammer",
                    "description": "Flashes firmware to widgets",
                    "price": 299.99,
                    "release_date": null,
                    "in_stock": null,
                    "picture": null,
                    "_links": {
                      "self": {
                        "href": "http://localhost/products/<id>"
                      },
                      "cg:relation": [{
                        "href": "http://localhost/products/<id>/invoices",
                        "name": "invoices"
                      }],
                      "cg:content": [{
                        "href": "http://localhost/products/<id>/picture",
                        "name": "picture"
                      }],
                      "curies": [
                        {
                          "href": "https://contentgrid.cloud/rels/contentgrid/{rel}",
                          "name": "cg",
                          "templated": true
                        },
                        {
                          "href": "https://contentgrid.cloud/rels/blueprint/{rel}",
                          "name": "blueprint",
                          "templated": true
                        },
                        {
                            "href": "https://contentgrid.cloud/rels/automation/{rel}",
                            "name": "automation",
                            "templated": true
                        }
                      ]
                    }
                  }
                }
                """;

    private static final String DELETED = """
                {
                  "trigger": "delete",
                  "old": {
                    "id": "<id>",
                    "name": "Widget Reprogrammer",
                    "description": "Flashes firmware to widgets",
                    "price": 299.99,
                    "release_date": null,
                    "in_stock": null,
                    "picture": null,
                    "_links": {
                      "self": {
                        "href": "http://localhost/products/<id>"
                      },
                      "cg:relation": [{
                        "href": "http://localhost/products/<id>/invoices",
                        "name": "invoices"
                      }],
                      "cg:content": [{
                        "href": "http://localhost/products/<id>/picture",
                        "name": "picture"
                      }],
                      "curies": [
                        {
                          "href": "https://contentgrid.cloud/rels/contentgrid/{rel}",
                          "name": "cg",
                          "templated": true
                        },
                        {
                          "href": "https://contentgrid.cloud/rels/blueprint/{rel}",
                          "name": "blueprint",
                          "templated": true
                        },
                        {
                            "href": "https://contentgrid.cloud/rels/automation/{rel}",
                            "name": "automation",
                            "templated": true
                        }
                      ]
                    }
                  },
                  "new": null
                }
                """;

    private static final String UPDATED = """
                {
                  "trigger": "update",
                  "old": {
                    "id": "<id>",
                    "name": "Widget Reprogrammer",
                    "description": "Flashes firmware to widgets",
                    "price": 299.99,
                    "release_date": null,
                    "in_stock": null,
                    "picture": null,
                    "_links": {
                      "self": {
                        "href": "http://localhost/products/<id>"
                      },
                      "cg:relation": [{
                        "href": "http://localhost/products/<id>/invoices",
                        "name": "invoices"
                      }],
                      "cg:content": [{
                        "href": "http://localhost/products/<id>/picture",
                        "name": "picture"
                      }],
                      "curies": [
                        {
                          "href": "https://contentgrid.cloud/rels/contentgrid/{rel}",
                          "name": "cg",
                          "templated": true
                        },
                        {
                          "href": "https://contentgrid.cloud/rels/blueprint/{rel}",
                          "name": "blueprint",
                          "templated": true
                        },
                        {
                            "href": "https://contentgrid.cloud/rels/automation/{rel}",
                            "name": "automation",
                            "templated": true
                        }
                      ]
                    }
                  },
                  "new": {
                    "id": "<id>",
                    "name": "Widget Reprogrammer",
                    "description": "Flashes firmware to widgets",
                    "price": 300.00,
                    "release_date": null,
                    "in_stock": null,
                    "picture": null,
                    "_links": {
                      "self": {
                        "href": "http://localhost/products/<id>"
                      },
                      "cg:relation": [{
                        "href": "http://localhost/products/<id>/invoices",
                        "name": "invoices"
                      }],
                      "cg:content": [{
                        "href": "http://localhost/products/<id>/picture",
                        "name": "picture"
                      }],
                      "curies": [
                        {
                          "href": "https://contentgrid.cloud/rels/contentgrid/{rel}",
                          "name": "cg",
                          "templated": true
                        },
                        {
                          "href": "https://contentgrid.cloud/rels/blueprint/{rel}",
                          "name": "blueprint",
                          "templated": true
                        },
                        {
                            "href": "https://contentgrid.cloud/rels/automation/{rel}",
                            "name": "automation",
                            "templated": true
                        }
                      ]
                    }
                  }
                }
                """;
}
