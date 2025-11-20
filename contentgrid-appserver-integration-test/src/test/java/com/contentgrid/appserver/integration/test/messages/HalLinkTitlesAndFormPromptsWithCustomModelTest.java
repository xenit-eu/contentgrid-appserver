package com.contentgrid.appserver.integration.test.messages;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter.Operation;
import com.contentgrid.appserver.application.model.sortable.SortableField;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import com.contentgrid.appserver.application.model.values.SortableName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.integration.test.fixture.invoicing.InvoicingApiApplication;
import com.contentgrid.appserver.integration.test.messages.HalLinkTitlesAndFormPromptsWithCustomModelTest.LocalConfiguration;
import com.contentgrid.appserver.registry.ApplicationResolver;
import com.contentgrid.appserver.registry.SingleApplicationResolver;
import com.contentgrid.appserver.rest.test.WithMockJwt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.hateoas.MediaTypes;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@SpringBootTest(properties = {
        "contentgrid.events.rabbitmq.enabled=false",
})
@ContextConfiguration(classes = {
        InvoicingApiApplication.class,
        LocalConfiguration.class,
})
@AutoConfigureMockMvc
@WithMockJwt
class HalLinkTitlesAndFormPromptsWithCustomModelTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void sortPropertyFallback() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/profile/entity-with-sort")
                        .accept(MediaTypes.HAL_FORMS_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().json("""
                        {
                            _templates: {
                                search: {
                                    method: "GET",
                                    properties: [
                                        {
                                            name: "id"
                                        },
                                        {
                                            name: "_sort",
                                            type: "text",
                                            "options" : {
                                                "promptField" : "prompt",
                                                "valueField" : "value",
                                                "minItems" : 0,
                                                "inline" : [
                                                    {
                                                        "property" : "id",
                                                        "direction" : "asc",
                                                        "prompt" : "id ascending",
                                                        "value" : "id,asc"
                                                    }, {
                                                        "property" : "id",
                                                        "direction" : "desc",
                                                        "prompt" : "id descending",
                                                        "value" : "id,desc"
                                                    }
                                                ]
                                            }
                                        }
                                    ],
                                    target: "http://localhost/entity-with-sort"
                                },
                                "create-form": {
                                    method: "POST",
                                    properties: [
                                        {
                                            name: "sort",
                                            type: "text"
                                        }
                                    ],
                                    target: "http://localhost/entity-with-sort"
                                }
                            }
                        }
                        """))
                // Check that options is certainly not present in the create-form field
                .andExpect(MockMvcResultMatchers.jsonPath("$._templates.['create-form'].properties[0].options")
                        .doesNotExist());
    }

    @Test
    void noSortPropertyWithoutCollectionFilterParams() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/profile/entity-without-filters")
                        .accept(MediaTypes.HAL_FORMS_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().json("""
                        {
                            _templates: {
                                search: {
                                    method: "GET",
                                    properties: [],
                                    target: "http://localhost/entity-without-filters"
                                },
                                "create-form": {
                                    method: "POST",
                                    properties: [
                                        {
                                            name: "name",
                                            type: "text"
                                        }
                                    ],
                                    target: "http://localhost/entity-without-filters"
                                }
                            }
                        }
                        """))
                // Check that there are no search or sort parameters present
                .andExpect(MockMvcResultMatchers.jsonPath("$._templates.search.properties")
                        .isEmpty());
    }


    private static final Entity ENTITY_WITH_SORT = Entity.builder()
            .name(EntityName.of("entity-with-sort"))
            .table(TableName.of("entity_with_sort"))
            .pathSegment(PathSegmentName.of("entity-with-sort"))
            .linkName(LinkName.of("entity-with-sort"))
            .attribute(SimpleAttribute.builder()
                    .name(AttributeName.of("sort"))
                    .column(ColumnName.of("sort"))
                    .type(Type.TEXT)
                    .build())
            .searchFilter(AttributeSearchFilter.builder()
                    .attributePath(PropertyPath.of(AttributeName.of("id")))
                    .name(FilterName.of("id"))
                    .operation(Operation.EXACT)
                    .build())
            .sortableField(SortableField.builder()
                    .propertyPath(PropertyPath.of(AttributeName.of("id")))
                    .name(SortableName.of("id"))
                    .build())
            .build();

    private static final Entity ENTITY_WITHOUT_FILTERS = Entity.builder()
            .name(EntityName.of("entity-without-filters"))
            .table(TableName.of("entity_without_filters"))
            .pathSegment(PathSegmentName.of("entity-without-filters"))
            .linkName(LinkName.of("entity-without-filters"))
            .attribute(SimpleAttribute.builder()
                    .name(AttributeName.of("name"))
                    .column(ColumnName.of("name"))
                    .type(Type.TEXT)
                    .build())
            .build();

    private static final Application APPLICATION = Application.builder()
            .name(ApplicationName.of("default"))
            .entity(ENTITY_WITH_SORT)
            .entity(ENTITY_WITHOUT_FILTERS)
            .build();

    @Configuration(proxyBeanMethods = false)
    static class LocalConfiguration {

        @Bean
        ApplicationResolver testApplicationResolver() {
            return new SingleApplicationResolver(APPLICATION);
        }

    }
}
