package com.contentgrid.appserver.rest;

import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.APPLICATION;
import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.PRODUCT;
import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.domain.data.DataEntry.DecimalDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.PlainDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.StringDataEntry;
import com.contentgrid.appserver.domain.data.EntityInstance;
import com.contentgrid.appserver.domain.events.EntityFormatter;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.example.ContentgridApp;
import com.contentgrid.appserver.registry.SingleApplicationResolver;
import com.contentgrid.appserver.rest.PermissionsPropagationTest.TestConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SequencedMap;
import java.util.UUID;
import lombok.Data;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@SpringBootTest(classes = {ContentgridApp.class, TestConfig.class}, properties = {
        "contentgrid.security.unauthenticated.allow=true",
        "contentgrid.security.csrf.disabled=true",
        "contentgrid.appserver.content-store.type=ephemeral",
})
@AutoConfigureMockMvc
class RestFormatterTest {

    @Autowired
    EntityFormatter entityFormatter;

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        public SingleApplicationResolver singleApplicationResolver() {
            return new SingleApplicationResolver(APPLICATION);
        }
    }

    @Test
    void testEntityDataSerialization() throws JsonProcessingException {
        var uuid = UUID.fromString("69415bf7-9aba-4a35-b677-0d66f3bec2bf");
        var entity = new TestEntityInstance(
                EntityIdentity.forEntity(PRODUCT.getName(), EntityId.of(uuid)),
                new LinkedHashMap<>(Map.of(
                        "name", new StringDataEntry("Widget Reprogrammer"),
                        "price", new DecimalDataEntry(BigDecimal.valueOf(299.99))
                ))
        );
        var actual = entityFormatter.format(APPLICATION, entity);
        var mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);
        var expected = mapper.readTree(EXPECTED);
        assertThat(actual).isEqualTo(expected);
    }

    @Data
    static class TestEntityInstance implements EntityInstance {
        final EntityIdentity identity;
        final SequencedMap<String, PlainDataEntry> data;
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
                        }
                    ]
                }
            }
            """;
}