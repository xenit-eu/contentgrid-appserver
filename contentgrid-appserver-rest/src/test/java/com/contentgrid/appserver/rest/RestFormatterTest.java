package com.contentgrid.appserver.rest;

import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.APPLICATION;
import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.PRODUCT;
import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.domain.data.DataEntry.DecimalDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.ListDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.PlainDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.StringDataEntry;
import com.contentgrid.appserver.application.model.links.LinkIdentity.NamedLink;
import com.contentgrid.appserver.application.model.links.LinkIdentity.UnnamedLink;
import com.contentgrid.appserver.domain.data.EntityInstance;
import com.contentgrid.appserver.domain.data.EntityLinkData;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.rest.test.TestApplication;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.UUID;
import lombok.Data;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
class RestFormatterTest {

    @Autowired
    RestEntityFormatter entityFormatter;

    @Test
    void testEntityDataSerialization() throws JacksonException {
        var uuid = UUID.fromString("69415bf7-9aba-4a35-b677-0d66f3bec2bf");
        var entity = new TestEntityInstance(
                EntityIdentity.forEntity(PRODUCT.getName(), EntityId.of(uuid)),
                new LinkedHashMap<>(Map.of(
                        "name", new StringDataEntry("Widget Reprogrammer"),
                        "price", new DecimalDataEntry(BigDecimal.valueOf(299.99))
                )),
                List.of()
        );
        var actual = entityFormatter.format(APPLICATION, entity);
        var mapper = JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
        var expected = mapper.readTree(EXPECTED);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void testEntityLinksSerialization() throws JacksonException {
        var uuid = UUID.fromString("69415bf7-9aba-4a35-b677-0d66f3bec2bf");
        var entity = new TestEntityInstance(
                EntityIdentity.forEntity(PRODUCT.getName(), EntityId.of(uuid)),
                new LinkedHashMap<>(Map.of(
                        "name", new StringDataEntry("Widget Reprogrammer"),
                        "price", new DecimalDataEntry(BigDecimal.valueOf(299.99))
                )),
                List.of(
                        new EntityLinkData(
                                new NamedLink(URI.create("https://links.example/rel/category"), "category"),
                                URI.create("https://links.example/profile/category"),
                                "https://categories.example/product?value=tools"
                        ),
                        new EntityLinkData(
                                new UnnamedLink(URI.create("https://links.example/rel/preview")),
                                null,
                                "https://preview.example/render?src=widget-reprogrammer"
                        )
                )
        );
        var actual = entityFormatter.format(APPLICATION, entity);
        var mapper = JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
        var expected = mapper.readTree(EXPECTED_WITH_ENTITY_LINKS);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void testMultiValueAttributeSerialization() throws JacksonException {
        var uuid = UUID.fromString("69415bf7-9aba-4a35-b677-0d66f3bec2bf");
        var entity = new TestEntityInstance(
                EntityIdentity.forEntity(PRODUCT.getName(), EntityId.of(uuid)),
                new LinkedHashMap<>(Map.of(
                        "name", new StringDataEntry("Widget Reprogrammer"),
                        "tags", new ListDataEntry(List.of(
                                new StringDataEntry("urgent"), new StringDataEntry("ethias"))),
                        "labels", new ListDataEntry(List.of())
                )),
                List.of()
        );
        // Change events reuse this same formatter, so this also covers the event payload shape
        var actual = entityFormatter.format(APPLICATION, entity);
        var mapper = JsonMapper.builder().build();
        assertThat(actual.get("tags")).isEqualTo(mapper.readTree("[\"urgent\",\"ethias\"]"));
        assertThat(actual.get("labels")).isEqualTo(mapper.readTree("[]"));
    }

    @Data
    static class TestEntityInstance implements EntityInstance {
        final EntityIdentity identity;
        final SequencedMap<String, PlainDataEntry> data;
        final Collection<EntityLinkData> links;
    }

    private static final String EXPECTED = """
            {
                "id": "69415bf7-9aba-4a35-b677-0d66f3bec2bf",
                "name": "Widget Reprogrammer",
                "price": 299.99,
                "_links": {
                    "self": {
                        "href": "http://localhost/products/69415bf7-9aba-4a35-b677-0d66f3bec2bf"
                    },
                    "cg:relation": [
                        {
                            "href": "http://localhost/products/69415bf7-9aba-4a35-b677-0d66f3bec2bf/invoices",
                            "name": "invoices"
                        }
                    ],
                    "cg:content": [
                        {
                            "href": "http://localhost/products/69415bf7-9aba-4a35-b677-0d66f3bec2bf/picture",
                            "name": "picture"
                        }
                    ],
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
            """;

    private static final String EXPECTED_WITH_ENTITY_LINKS = """
            {
                "id": "69415bf7-9aba-4a35-b677-0d66f3bec2bf",
                "name": "Widget Reprogrammer",
                "price": 299.99,
                "_links": {
                    "self": {
                        "href": "http://localhost/products/69415bf7-9aba-4a35-b677-0d66f3bec2bf"
                    },
                    "cg:relation": [
                        {
                            "href": "http://localhost/products/69415bf7-9aba-4a35-b677-0d66f3bec2bf/invoices",
                            "name": "invoices"
                        }
                    ],
                    "cg:content": [
                        {
                            "href": "http://localhost/products/69415bf7-9aba-4a35-b677-0d66f3bec2bf/picture",
                            "name": "picture"
                        }
                    ],
                    "https://links.example/rel/category": [
                        {
                            "href": "https://categories.example/product?value=tools",
                            "name": "category",
                            "profile": "https://links.example/profile/category"
                        }
                    ],
                    "https://links.example/rel/preview": {
                        "href": "https://preview.example/render?src=widget-reprogrammer"
                    },
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
            """;
}