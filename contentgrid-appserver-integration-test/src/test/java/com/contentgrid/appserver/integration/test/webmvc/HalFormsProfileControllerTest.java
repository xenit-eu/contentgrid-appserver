package com.contentgrid.appserver.integration.test.webmvc;

import com.contentgrid.appserver.integration.test.fixture.invoicing.InvoicingApiApplication;
import com.contentgrid.appserver.rest.test.WithMockJwt;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
})
@AutoConfigureMockMvc(printOnlyOnFailure = false)
@WithMockJwt
class HalFormsProfileControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void profileController_embeddedContent_searchForm() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/profile/customers")
                        .accept(MediaTypes.HAL_FORMS_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().json("""
                        {
                            _links: {
                                describes: [
                                    {
                                        name: "collection",
                                        href: "http://localhost/customers"
                                    },
                                    {
                                        name: "item",
                                        href: "http://localhost/customers/{instanceId}",
                                        templated: true
                                    }
                                ]
                            },
                            _templates: {
                                "create-form": {}, # tested in subclasses
                                search: {
                                    method: "GET",
                                    target: "http://localhost/customers",
                                    properties: [
                                        {
                                            name: "vat",
                                            type: "text"
                                        },
                                        {
                                            name: "content.size",
                                            type: "number"
                                        },
                                        {
                                            name: "content.mimetype",
                                            type: "text"
                                        },
                                        {
                                            name: "content.filename",
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
                                        {
                                            name: "invoices.number",
                                            type: "text"
                                        },
                                        {
                                            name: "invoices.paid",
                                            type: "checkbox"
                                        },
                                        # invoices.content.length does not run against the max depth limit because
                                        # they are static parameters on a field at depth 1
                                        {
                                            name: "invoices.content.length",
                                            type: "number"
                                        },
                                        # These have predicate=None set, so they are not present in the documentation
                                        #{
                                        #    name: "invoices.content.length.lt",
                                        #    type: "number"
                                        #},
                                        #{
                                        #    name: "invoices.content.length.gt",
                                        #    type: "number"
                                        #},
                                        # this is also a static parameter on a field at depth 1, so it is present
                                        {
                                            name: "invoices.orders.id"
                                        },
                                        {
                                            name: "_sort"
                                        }
                                    ]
                                }
                            }
                        }
                        """));
    }

    @Test
    void profileController_requiredAssociation_searchForm() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/profile/invoices")
                        .accept(MediaTypes.HAL_FORMS_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().json("""
                        {
                            _templates: {
                                "create-form": {}, # tested in subclasses
                                search: {
                                    method: "GET",
                                    target: "http://localhost/invoices",
                                    properties: [
                                        {
                                            name: "number",
                                            type: "text"
                                        },
                                        {
                                            name: "paid"
                                            # ,type: "checkbox"
                                        },
                                        {
                                            name: "content.length",
                                            type: "number"
                                        },
                                        # These have predicate=None set, so they are not present in the documentation
                                        #{
                                        #    name: "content.length.lt",
                                        #    type: "number"
                                        #},
                                        #{
                                        #    name: "content.length.gt",
                                        #    type: "number"
                                        #}
                                        # Note: no relation to orders is exposed,
                                        # because the searchable properties on Order are
                                        # also relations. We only expand one level deep
                                        # orders.id itself is exposed directly on invoice itself, so it is present
                                        {
                                            name: "orders.id"
                                        },
                                        {
                                            name: "_sort",
                                            options: {
                                                minItems: 0,
                                                inline: [
                                                    {
                                                        value: "number,asc",
                                                        property: "number",
                                                        direction: "asc"
                                                    },
                                                    {
                                                        value: "number,desc",
                                                        property: "number",
                                                        direction: "desc"
                                                    },
                                                    {
                                                        value: "paid,asc",
                                                        property: "paid",
                                                        direction: "asc"
                                                    },
                                                    {
                                                        value: "paid,desc",
                                                        property: "paid",
                                                        direction: "desc"
                                                    } #, TODO: re-enable in ACC-2403
                                                    #{
                                                    #    value: "content.length,asc",
                                                    #    property: "content.length",
                                                    #    direction: "asc"
                                                    #},
                                                    #{
                                                    #    value: "content.length,desc",
                                                    #    property: "content.length",
                                                    #    direction: "desc"
                                                    #}
                                                ]
                                            }
                                        }
                                    ]
                                }
                            }
                        }
                        """));
    }

    @Test
    void profileController_noContent_createForm() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/profile/orders")
                        .accept(MediaTypes.HAL_FORMS_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().json("""
                        {
                            _templates: {
                                "create-form": {
                                    method: "POST",
                                    target: "http://localhost/orders",
                                    contentType: "application/json", # Always JSON, because there is no content property
                                    properties: [
                                        {
                                            name: "customer",
                                            type: "url"
                                        },
                                        {
                                            name: "shipping_address",
                                            type: "url"
                                        } #, TODO: re-enable in ACC-2311
                                        ## These many-to-many are still present because they are not ignored
                                        #{
                                        #    name: "promos",
                                        #    type: "url"
                                        #},
                                        #{
                                        #    name: "manualPromos",
                                        #    type: "url"
                                        #}
                                    ]
                                }
                            }
                        }
                        """));
    }

    @Test
    void profileController_orderedSearchParams() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/profile/customers")
                        .accept(MediaTypes.HAL_FORMS_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$._templates.search.properties[0].name",
                        Matchers.is("vat")))
                .andExpect(MockMvcResultMatchers.jsonPath("$._templates.search.properties[1].name",
                        Matchers.is("content.size")))
                .andExpect(MockMvcResultMatchers.jsonPath("$._templates.search.properties[2].name",
                        Matchers.is("content.mimetype")))
                .andExpect(MockMvcResultMatchers.jsonPath("$._templates.search.properties[3].name",
                        Matchers.is("content.filename")))
                .andExpect(MockMvcResultMatchers.jsonPath("$._templates.search.properties[4].name",
                        Matchers.is("birthday")))
                .andExpect(MockMvcResultMatchers.jsonPath("$._templates.search.properties[5].name",
                        Matchers.is("gender")))
                .andExpect(MockMvcResultMatchers.jsonPath("$._templates.search.properties[6].name",
                        Matchers.is("invoices.number")))
                .andExpect(MockMvcResultMatchers.jsonPath("$._templates.search.properties[7].name",
                        Matchers.is("invoices.paid")))
                .andExpect(MockMvcResultMatchers.jsonPath("$._templates.search.properties[8].name",
                        Matchers.is("invoices.content.length")))
                .andExpect(MockMvcResultMatchers.jsonPath("$._templates.search.properties[9].name",
                        Matchers.is("invoices.orders.id")))
                .andExpect(MockMvcResultMatchers.jsonPath("$._templates.search.properties[10].name",
                        Matchers.is("_sort")));
    }

    @Test
    void profileController_entityInformation_withEmbeddedContent() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/profile/customers").accept(MediaTypes.HAL_FORMS_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().json("""
                        {
                            name: "customer",
                            title: "Client",
                            description: "Company or person that acts like a client",
                            _embedded: {
                                "blueprint:attribute": [
                                    {
                                        name: "id",
                                        type: "string",
                                        readOnly: true,
                                        required: false
                                    },
                                    {
                                        name: "audit_metadata",
                                        type: "object",
                                        readOnly: true,
                                        required: false,
                                        _embedded: {
                                            "blueprint:attribute": [
                                                {
                                                    name: "created_by",
                                                    type: "string",
                                                    readOnly: true,
                                                    required: false
                                                },
                                                {
                                                    name: "created_date",
                                                    type: "datetime",
                                                    readOnly: true,
                                                    required: false
                                                },
                                                {
                                                    name: "last_modified_by",
                                                    type: "string",
                                                    readOnly: true,
                                                    required: false
                                                },
                                                {
                                                    name: "last_modified_date",
                                                    type: "datetime",
                                                    readOnly: true,
                                                    required: false
                                                }
                                            ]
                                        }
                                    },
                                    {
                                        name: "name",
                                        title: "Customer name",
                                        type: "string",
                                        description: "Full name of the customer",
                                        readOnly: false,
                                        required: false
                                    },
                                    {
                                        name: "vat",
                                        title: "VAT number",
                                        type: "string",
                                        description: "VAT number of the customer",
                                        readOnly: false,
                                        required: true,
                                        _embedded: {
                                            "blueprint:constraint": [
                                                {
                                                    type: required
                                                },
                                                {
                                                    type: unique
                                                }
                                            ],
                                            "blueprint:search-param": [
                                                {
                                                    name: "vat",
                                                    title: "VAT number",
                                                    type: "exact-match"
                                                }
                                            ]
                                        }
                                    },
                                    {
                                        name: "content",
                                        type: "object",
                                        readOnly: false,
                                        required: false,
                                        _embedded: {
                                            "blueprint:attribute": [
                                                {
                                                    name: "length",
                                                    title: "length",
                                                    type: "long",
                                                    readOnly: true,
                                                    required: false,
                                                    _embedded: {
                                                        "blueprint:search-param": [
                                                            {
                                                                name: "content.size",
                                                                type: "exact-match"
                                                            }
                                                        ]
                                                    }
                                                },
                                                {
                                                    name: "mimetype",
                                                    title: "mimetype",
                                                    type: "string",
                                                    readOnly: false,
                                                    required: false,
                                                    _embedded: {
                                                        "blueprint:search-param": [
                                                            {
                                                                name: "content.mimetype",
                                                                title: "Customer Document Mimetype",
                                                                type: "exact-match"
                                                            }
                                                        ]
                                                    }
                                                },
                                                {
                                                    name: "filename",
                                                    title: "filename",
                                                    type: "string",
                                                    readOnly: false,
                                                    required: false,
                                                    _embedded: {
                                                        "blueprint:search-param": [
                                                            {
                                                                name: "content.filename",
                                                                title: "Customer Document Filename",
                                                                type: "exact-match"
                                                            }
                                                        ]
                                                    }
                                                }
                                            ]
                                        }
                                    },
                                    {
                                        name: "birthday",
                                        type: "datetime",
                                        readOnly: false,
                                        required: false,
                                        _embedded: {
                                            "blueprint:search-param": [
                                                {
                                                    name: "birthday",
                                                    type: "exact-match"
                                                }
                                            ]
                                        }
                                    },
                                    {
                                        name: "gender",
                                        type: "string",
                                        readOnly: false,
                                        required: false,
                                        _embedded: {
                                            "blueprint:constraint": [
                                                {
                                                    type: "allowed-values",
                                                    values: ["female", "male"]
                                                }
                                            ],
                                            "blueprint:search-param": [
                                                {
                                                    name: "gender",
                                                    type: "exact-match"
                                                }
                                            ]
                                        }
                                    },
                                    {
                                        name: "total_spend",
                                        title: "Total Amount Spent",
                                        type: "long",
                                        description: "Total amount of money spent (in euros)",
                                        readOnly: false,
                                        required: false
                                    }
                                ],
                                "blueprint:relation": [
                                    {
                                        name: "orders",
                                        many_source_per_target: false,
                                        many_target_per_source: true,
                                        required: false,
                                        _links: {
                                            "blueprint:target-entity": {
                                                href: "http://localhost/profile/orders"
                                            }
                                        }
                                    },
                                    {
                                        name: "invoices",
                                        many_source_per_target: false,
                                        many_target_per_source: true,
                                        required: false,
                                        _links: {
                                            "blueprint:target-entity": {
                                                href: "http://localhost/profile/invoices"
                                            }
                                        }
                                    }
                                ]
                            }
                        }
                        """));
    }

    @Test
    void profileController_entityInformation_withInlineContent() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/profile/invoices").accept(MediaTypes.HAL_FORMS_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().json("""
                        {
                            name: "invoice",
                            description: "A bill containing a counterparty and several orders",
                            _embedded: {
                                "blueprint:attribute": [
                                    {
                                        name: "id",
                                        type: "string",
                                        readOnly: true,
                                        required: false
                                    },
                                    {
                                        name: "audit_metadata",
                                        type: "object",
                                        readOnly: true,
                                        required: false,
                                        _embedded: {
                                            "blueprint:attribute": [
                                                {
                                                    name: "created_by",
                                                    type: "string",
                                                    readOnly: true,
                                                    required: false
                                                },
                                                {
                                                    name: "created_date",
                                                    type: "datetime",
                                                    readOnly: true,
                                                    required: false
                                                },
                                                {
                                                    name: "last_modified_by",
                                                    type: "string",
                                                    readOnly: true,
                                                    required: false
                                                },
                                                {
                                                    name: "last_modified_date",
                                                    type: "datetime",
                                                    readOnly: true,
                                                    required: false
                                                }
                                            ]
                                        }
                                    },
                                    {
                                        name: "number",
                                        type: "string",
                                        description: "Identifier of the invoice",
                                        readOnly: false,
                                        required: true,
                                        _embedded: {
                                            "blueprint:constraint": [
                                                {
                                                    type: "required"
                                                }
                                            ],
                                            "blueprint:search-param": [
                                                {
                                                    name: "number",
                                                    type: "exact-match"
                                                }
                                            ]
                                        }
                                    },
                                    {
                                        name: "draft",
                                        type: "boolean",
                                        readOnly: false,
                                        required: false
                                    },
                                    {
                                        name: "paid",
                                        type: "boolean",
                                        readOnly: false,
                                        required: false,
                                        _embedded: {
                                            "blueprint:search-param": [
                                                {
                                                    name: "paid",
                                                    type: "exact-match"
                                                }
                                            ]
                                        }
                                    },
                                    {
                                        name: "content",
                                        type: "object",
                                        readOnly: false,
                                        required: false,
                                        _embedded: {
                                            "blueprint:attribute": [
                                                {
                                                    name: "length",
                                                    type: "long",
                                                    readOnly: true,
                                                    required: false,
                                                    _embedded: {
                                                        "blueprint:search-param": [
                                                            {
                                                                name: "content.length",
                                                                type: "exact-match"
                                                            }
                                                        ]
                                                    }
                                                },
                                                {
                                                    name: "mimetype",
                                                    type: "string",
                                                    readOnly: false,
                                                    required: false
                                                },
                                                {
                                                    name: "filename",
                                                    type: "string",
                                                    readOnly: false,
                                                    required: false
                                                }
                                            ]
                                        }
                                    },
                                    {
                                        name: "attachment",
                                        type: "object",
                                        readOnly: false,
                                        required: false,
                                        _embedded: {
                                            "blueprint:attribute": [
                                                {
                                                    name: "length",
                                                    type: "long",
                                                    readOnly: true,
                                                    required: false
                                                },
                                                {
                                                    name: "mimetype",
                                                    type: "string",
                                                    readOnly: false,
                                                    required: false
                                                },
                                                {
                                                    name: "filename",
                                                    type: "string",
                                                    readOnly: false,
                                                    required: false
                                                }
                                            ]
                                        }
                                    }
                                ],
                                "blueprint:relation": [
                                    {
                                        name: "counterparty",
                                        description: "Client that has to pay the invoice",
                                        many_source_per_target: true,
                                        many_target_per_source: false,
                                        required: true,
                                        _links: {
                                            "blueprint:target-entity": {
                                                href: "http://localhost/profile/customers"
                                            }
                                        }
                                    },
                                    {
                                        name: "orders",
                                        description: "Orders of the invoice",
                                        many_source_per_target: false,
                                        many_target_per_source: true,
                                        required: false,
                                        _links: {
                                            "blueprint:target-entity": {
                                                href: "http://localhost/profile/orders"
                                            }
                                        }
                                    },
                                    {
                                        name: "refund",
                                        many_source_per_target: false,
                                        many_target_per_source: false,
                                        required: false,
                                        _links: {
                                            "blueprint:target-entity": {
                                                href: "http://localhost/profile/refunds"
                                            }
                                        }
                                    }
                                ]
                            }
                        }
                        """));
    }

    @Test
    void profileController_entityInformation_entityNameWithDashes() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/profile/shipping-labels").accept(MediaTypes.HAL_FORMS_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().json("""
                        {
                            name: "shipping-label",
                            _embedded: {
                                "blueprint:attribute": [
                                    {
                                        name: "id",
                                        type: "string",
                                        readOnly: true,
                                        required: false
                                    },
                                    {
                                        name: "from",
                                        type: "string",
                                        readOnly: false,
                                        required: true,
                                        _embedded: {
                                            "blueprint:constraint": [
                                                {
                                                    type: "required"
                                                }
                                            ],
                                            "blueprint:search-param": [
                                                {
                                                    name: "from",
                                                    type: "exact-match"
                                                }
                                            ]
                                        }
                                    },
                                    {
                                        name: "to",
                                        type: "string",
                                        readOnly: false,
                                        required: true,
                                        _embedded: {
                                            "blueprint:constraint": [
                                                {
                                                    type: "required"
                                                }
                                            ],
                                            "blueprint:search-param": [
                                                {
                                                    name: "to",
                                                    type: "exact-match"
                                                }
                                            ]
                                        }
                                    },
                                    {
                                        name: "barcode_picture",
                                        type: "object",
                                        readOnly: false,
                                        required: false,
                                        _embedded: {
                                            "blueprint:attribute": [
                                                {
                                                    name: "length",
                                                    title: "length",
                                                    type: "long",
                                                    readOnly: true,
                                                    required: false,
                                                    _embedded: {
                                                        "blueprint:search-param": [] # assert empty, because CollectionFilterParam is missing on barcode_picture
                                                    }
                                                },
                                                {
                                                    name: "mimetype",
                                                    title: "mimetype",
                                                    type: "string",
                                                    readOnly: false,
                                                    required: false,
                                                    _embedded: {
                                                        "blueprint:search-param": []
                                                    }
                                                },
                                                {
                                                    name: "filename",
                                                    title: "filename",
                                                    type: "string",
                                                    readOnly: false,
                                                    required: false,
                                                    _embedded: {
                                                        "blueprint:search-param": []
                                                    }
                                                }
                                            ]
                                        }
                                    },
                                    {
                                        name: "package",
                                        type: "object",
                                        readOnly: false,
                                        required: false,
                                        _embedded: {
                                            "blueprint:attribute": [
                                                {
                                                    name: "length",
                                                    title: "length",
                                                    type: "long",
                                                    readOnly: true,
                                                    required: false,
                                                    _embedded: {
                                                        "blueprint:search-param": []
                                                    }
                                                },
                                                {
                                                    name: "mimetype",
                                                    title: "mimetype",
                                                    type: "string",
                                                    readOnly: false,
                                                    required: false,
                                                    _embedded: {
                                                        "blueprint:search-param": []
                                                    }
                                                },
                                                {
                                                    name: "filename",
                                                    title: "filename",
                                                    type: "string",
                                                    readOnly: false,
                                                    required: false,
                                                    _embedded: {
                                                        "blueprint:search-param": []
                                                    }
                                                }
                                            ]
                                        }
                                    }
                                ],
                                "blueprint:relation": [
                                    {
                                        name: "parent",
                                        many_source_per_target: true,
                                        many_target_per_source: false,
                                        required: false,
                                        _links: {
                                            "blueprint:target-entity": {
                                                href: "http://localhost/profile/shipping-labels"
                                            }
                                        }
                                    }
                                ]
                            }
                        }
                        """));
    }

    @Test
    void profileController_embeddedContent() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/profile/customers")
                        .accept(MediaTypes.HAL_FORMS_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().json("""
                        {
                            _links: {
                                describes: [
                                    {
                                        name: "collection",
                                        href: "http://localhost/customers"
                                    },
                                    {
                                        name: "item",
                                        href: "http://localhost/customers/{instanceId}",
                                        templated: true
                                    }
                                ]
                            },
                            _templates: {
                                "create-form": {
                                    method: "POST",
                                    contentType: "multipart/form-data",
                                    target: "http://localhost/customers",
                                    properties: [
                                        {
                                            name: "name",
                                            type: "text"
                                        },
                                        {
                                            name: "vat",
                                            required: true,
                                            type: "text"
                                        },
                                        {
                                            name: "content",
                                            type: "file"
                                        },
                                        {
                                            name: "birthday",
                                            type: "datetime"
                                        },
                                        {
                                            name: "gender",
                                            type: "text",
                                            options: {
                                                inline: [ "female", "male" ]
                                            }
                                        },
                                        {
                                            name: "total_spend",
                                            type: "number"
                                        } #, TODO: re-enable in ACC-2311
                                        #{
                                        #    name: "orders",
                                        #    type: "url",
                                        #    options: {
                                        #        valueField: "/_links/self/href",
                                        #        minItems: 0,
                                        #        link: {
                                        #            href: "http://localhost/orders"
                                        #        }
                                        #    }
                                        #},
                                        #{
                                        #    name: "invoices",
                                        #    type: "url",
                                        #    options: {
                                        #        valueField: "/_links/self/href",
                                        #        minItems: 0,
                                        #        link: {
                                        #            href: "http://localhost/invoices"
                                        #        }
                                        #    }
                                        #}
                                    ]
                                },
                                search: {} # tested in other tests
                            }
                        }
                        """));
    }

    @Test
    void profileController_requiredAssociation() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/profile/invoices")
                        .accept(MediaTypes.HAL_FORMS_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().json("""
                        {
                            _templates: {
                                "create-form": {
                                    method: "POST",
                                    contentType: "multipart/form-data",
                                    target: "http://localhost/invoices",
                                    properties: [
                                        {
                                            name: "number",
                                            required: true,
                                            type: "text"
                                        },
                                        {
                                            name: "draft"
                                            # ,type: "checkbox"
                                        },
                                        {
                                            name: "paid"
                                            # ,type: "checkbox"
                                        },
                                        {
                                            name: "content",
                                            type: "file"
                                        },
                                        {
                                           name: "attachment",
                                           type: "file"
                                        },
                                        {
                                            name: "counterparty",
                                            type: "url",
                                            required: true,
                                            options: {
                                                valueField: "/_links/self/href",
                                                minItems: 1,
                                                maxItems: 1,
                                                link: {
                                                    href: "http://localhost/customers"
                                                }
                                            }
                                        },
                                        {
                                            name: "refund",
                                            type: "url",
                                            options: {
                                                valueField: "/_links/self/href",
                                                minItems: 0,
                                                maxItems: 1,
                                                link: {
                                                    href: "http://localhost/refunds"
                                                }
                                            }
                                        } #, TODO: re-enable in ACC-2311
                                        #{
                                        #    name: "orders",
                                        #    type: "url",
                                        #    options: {
                                        #        valueField: "/_links/self/href",
                                        #        minItems: 0,
                                        #        link: {
                                        #            href: "http://localhost/orders"
                                        #        }
                                        #    }
                                        #}
                                    ]
                                },
                                search: {} # tested in other tests
                            }
                        }
                        """));
    }
}
