package com.contentgrid.appserver.integration.test.messages;

import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter.Operation;
import com.contentgrid.appserver.application.model.sortable.SortableField;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import com.contentgrid.appserver.application.model.values.SortableName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.integration.test.fixture.invoicing.InvoicingApi;
import com.contentgrid.appserver.integration.test.fixture.invoicing.InvoicingApiApplication;
import com.contentgrid.appserver.integration.test.messages.HalLinkTitlesAndFormPromptsTest.LocalConfiguration;
import com.contentgrid.appserver.rest.test.WithMockJwt;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
        LocalConfiguration.class
})
@AutoConfigureMockMvc(printOnlyOnFailure = false)
@WithMockJwt
class HalLinkTitlesAndFormPromptsTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    InvoicingApi invoicingApi;

    EntityId customerId;
    EntityId invoiceId;
    EntityId shippingLabelId;

    @BeforeEach
    void setup() throws InvalidPropertyDataException {
        customerId = invoicingApi.createCustomer("Abc", "ABC").getIdentity().getEntityId();
        invoiceId = invoicingApi.createInvoice("12345678", true, true, customerId, Set.of()).getIdentity().getEntityId();
        shippingLabelId = invoicingApi.createShippingLabel("here", "there").getIdentity().getEntityId();
    }

    @AfterEach
    void cleanup() {
        invoicingApi.deleteInvoice(invoiceId);
        invoicingApi.deleteCustomer(customerId);
        invoiceId = null;
        customerId = null;
    }

    @Test
    void promptOnCreateFormPropertiesInHalForms() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/profile/customers")
                        .accept(MediaTypes.HAL_FORMS_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().json("""
                        {
                            _templates: {
                                search: {
                                    method: "GET",
                                    properties: [
                                        {
                                            prompt: "VAT number",
                                            name: "vat",
                                            type: "text"
                                        },
                                        {
                                            name: "birthday",
                                            type: "datetime"
                                        },
                                        {
                                            name: "gender",
                                            type: "text"
                                        },
                                        { name: "content.size", type: "number" }, { name: "content.mimetype", type: "text" }, { name: "content.filename", type: "text" },
                                        { name: "invoices.number", type: "text" }, { name: "invoices.paid", type: "checkbox" }, { name: "invoices.orders.id" }, { name: "invoices.content.length", type: "number" },
                                        {
                                            name: "sort",
                                            options: {
                                                promptField: "prompt",
                                                valueField: "value",
                                                inline: [
                                                    {
                                                        value: "vat,asc",
                                                        prompt: "VAT number A→Z" # Note that the field value is replaced with the proper translation
                                                    },
                                                    {
                                                        value: "vat,desc",
                                                        prompt: "VAT number Z→A"
                                                    },
                                                    {
                                                        value: "birthday,asc",
                                                        prompt: "birthday oldest first"
                                                    },
                                                    {
                                                        value: "birthday,desc",
                                                        prompt: "birthday newest first"
                                                    },
                                                    {
                                                        value: "gender,asc",
                                                        prompt: "gender A→Z"
                                                    },
                                                    {
                                                        value: "gender,desc",
                                                        prompt: "gender Z→A"
                                                    },
                                                    {
                                                        value: "content.size,asc",
                                                        prompt: "content.size 0→9"
                                                    },
                                                    {
                                                        value: "content.size,desc",
                                                        prompt: "content.size 9→0"
                                                    },
                                                    {
                                                        value: "content.mimetype,asc",
                                                        prompt: "Customer Document Mimetype A→Z"
                                                    },
                                                    {
                                                        value: "content.mimetype,desc",
                                                        prompt: "Customer Document Mimetype Z→A"
                                                    },
                                                    {
                                                        value: "content.filename,asc",
                                                        prompt: "Customer Document Filename A→Z"
                                                    },
                                                    {
                                                        value: "content.filename,desc",
                                                        prompt: "Customer Document Filename Z→A"
                                                    }
                                                ]
                                            }
                                        }
                                    ]
                                },
                                create-form: {
                                    method: "POST",
                                    properties: [
                                        {
                                            prompt: "Customer name",
                                            name: "name",
                                            type: "text"
                                        },
                                        {
                                            prompt: "VAT number",
                                            name: "vat",
                                            type: "text"
                                        },
                                        {
                                            name: "content",
                                            type: "file"
                                        },
                                        {
                                            name : "birthday",
                                            type : "datetime"
                                        },
                                        {
                                            name: "gender",
                                            type: "radio",
                                            options: {
                                                inline: [ "female", "male" ]
                                            }
                                        },
                                        {
                                            prompt: "Spending Total",
                                            name : "total_spend",
                                            type : "number"
                                        },
                                        { name : "orders", type : "url" }, { name : "invoices", type : "url" }
                                    ]
                                }
                            }
                        }
                        """))
        ;
    }

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
                                            name: "sort",
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

    @Test
    void contentFieldCamelCasedInCreateForm() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/profile/shipping-labels")
                        .accept(MediaTypes.HAL_FORMS_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().json("""
                        {
                            _templates: {
                                search: {},
                                create-form: {
                                    method: "POST",
                                    properties: [
                                        {
                                            name: "from",
                                            type: "text",
                                            required: true
                                        },
                                        {
                                            name: "to",
                                            type: "text",
                                            required: true
                                        },
                                        {
                                            name: "parent",
                                            type: "url"
                                        },
                                        {
                                            name: "barcodePicture",
                                            type: "file"
                                        },
                                        {
                                            name: "_package",
                                            type: "file"
                                        }
                                    ]
                                }
                            }
                        }
                        """))
        ;
    }

    @Test
    void titleOnCgEntityInHalForms() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/profile"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().json("""
                        {
                            _links: {
                                "cg:entity": [
                                    {
                                        name: "customers",
                                        title: "Client"
                                    },
                                    { name: "invoices" }, { name: "refunds" }, { name: "promotions" },
                                    { name: "shipping-addresses" }, { name: "shipping-labels" }, { name: "orders" },
                                    { name: "entity-with-sort" }, { name: "entity-without-filters" }
                                ]
                            }
                        }
                        """))
        ;
    }

    @Test
    void titleOnCgRelationInHalForms() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/invoices/" + invoiceId).accept(MediaTypes.HAL_FORMS_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().json("""
                        {
                            _links: {
                                "cg:relation": [
                                    {
                                        name: "counterparty",
                                        title: "Sent by"
                                    },
                                    { name: "orders" }, { name: "refund" }
                                ]
                            }
                        }
                        """))
                ;
    }

    @Test
    void titleOnCgContentInHalForms() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/invoices/" + invoiceId).accept(MediaTypes.HAL_FORMS_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().json("""
                        {
                            _links: {
                                "cg:content": [
                                    {
                                        name: "attachment",
                                        title: "Attached File"
                                    },
                                    { name: "content" }
                                ]
                            }
                        }
                        """))
        ;
    }

    @Test
    void promptOnCgContentPropertiesInHalForms() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/invoices/" + invoiceId).accept(MediaTypes.HAL_FORMS_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andDo(res -> System.out.println(res.getResponse().getContentAsString()))
                .andExpect(MockMvcResultMatchers.content().json("""
                        {
                            _templates: {
                                "default": {
                                    properties: [
                                        {
                                            prompt: "Attached File Filename",
                                            name: "attachment_filename",
                                            type: "text"
                                        },
                                        {
                                            prompt: "Attached File Mimetype",
                                            name: "attachment_mimetype",
                                            type: "text"
                                        },
                                        {},{},{},{},{}
                                    ]
                                }
                            }
                        }
                        """))
        ;
    }

    @Test
    void titleOnEntityAndPropertiesInJsonSchema() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/profile/customers")
                        .accept("application/schema+json"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().json("""
                        {
                            title : "Client",
                            properties : {
                                name : {
                                    title : "Customer name",
                                    readOnly : false,
                                    type : "string"
                                },
                                vat : {
                                    title : "VAT number",
                                    readOnly : false,
                                    type : "string"
                                },
                                birthday : {
                                    title : "Birthday",
                                    readOnly : false,
                                    type : "string",
                                    format : "date-time"
                                },
                                total_spend : {
                                    title : "Total Amount Spent",
                                    readOnly : false,
                                    type : "integer"
                                }
                            }
                        }
                        """))
        ;
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


    @Configuration(proxyBeanMethods = false)
    static class LocalConfiguration {

        // TODO: add ENTITY_WITH_SORT and ENTITY_WITHOUT_FILTERS to application

    }
}
