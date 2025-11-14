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
import com.contentgrid.appserver.events.RabbitMqEventHandlersTest.TestConfig;
import com.contentgrid.appserver.example.ContentgridApp;
import com.contentgrid.appserver.query.engine.api.TableCreator;
import com.contentgrid.appserver.registry.SingleApplicationResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;


@SpringBootTest(classes = {ContentgridApp.class, TestConfig.class}, properties = {
        "csrf.disabled=true",
        "contentgrid.security.unauthenticated.allow=true",
        "contentgrid.thunx.abac.source=none",
        "contentgrid.appserver.content-store.type=ephemeral",
        "spring.rabbitmq.host=foo",
})
@AutoConfigureMockMvc
class RabbitMqEventHandlersTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatamodelApi datamodelApi;

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        public SingleApplicationResolver singleApplicationResolver() {
            return new SingleApplicationResolver(APPLICATION);
        }

    }

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
                        }
                      ]
                    }
                  }
                }
                """;
}
