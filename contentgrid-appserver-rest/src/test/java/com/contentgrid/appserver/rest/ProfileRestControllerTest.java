package com.contentgrid.appserver.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.registry.ApplicationResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.hateoas.MediaTypes;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(printOnlyOnFailure = false)
class ProfileRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
    private ApplicationResolver resolver;

    @BeforeEach
    void setup() {
        Mockito.when(resolver.resolve(Mockito.any()))
                .thenReturn(ModelTestFixtures.APPLICATION);
    }

    @Test
    void getProfileRoot() throws Exception {
        mockMvc.perform(get("/profile").accept(MediaTypes.HAL_FORMS_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.self.href").value("http://localhost/profile"))
                .andExpect(jsonPath("$._links.cg:entity[?(@.name=='persons')].href").value("http://localhost/profile/persons"))
                .andExpect(jsonPath("$._links.cg:entity[?(@.name=='invoices')].href").value("http://localhost/profile/invoices"))
                .andExpect(jsonPath("$._links.cg:entity[?(@.name=='products')].href").value("http://localhost/profile/products"));
    }

    @Test
    void getProfileRoot_singleEntity() throws Exception {
        Mockito.when(resolver.resolve(Mockito.any()))
                .thenReturn(Application.builder()
                        .name(ApplicationName.of("test-application"))
                        .entity(Entity.builder()
                                .name(EntityName.of("single-entity"))
                                .table(TableName.of("single_entity"))
                                .pathSegment(PathSegmentName.of("single-entities"))
                                .linkName(LinkName.of("single-entities"))
                                .build())
                        .build());
        mockMvc.perform(get("/profile").accept(MediaTypes.HAL_FORMS_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.cg:entity").isArray());
    }

    @Test
    void getProfileRoot_noEntities() throws Exception {
        Mockito.when(resolver.resolve(Mockito.any()))
                .thenReturn(Application.builder()
                        .name(ApplicationName.of("test-application"))
                        .build());
        mockMvc.perform(get("/profile").accept(MediaTypes.HAL_FORMS_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.cg:entity").doesNotExist());
    }

    @Test
    void getProfileEntity_blueprintInformation() throws Exception {
        mockMvc.perform(get("/profile/invoices").accept(MediaTypes.HAL_FORMS_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                            name: "invoice",
                            description: null,
                            _embedded: {
                                "blueprint:attribute": [{
                                    name: "id",
                                    type: "string",
                                    readOnly: true
                                }, {
                                    name: "number",
                                    type: "string",
                                    required: true,
                                    _embedded: {
                                        "blueprint:constraint": [{
                                            type: "required"
                                        }, {
                                            type: "unique"
                                        }],
                                        "blueprint:search-param": [{
                                            name: "number",
                                            type: "exact"
                                        }]
                                    }
                                }, {
                                    name: "amount",
                                    type: "double",
                                    required: true,
                                    _embedded: {
                                        "blueprint:constraint": [{
                                            type: "required"
                                        }]
                                    }
                                }, {
                                    name: "received",
                                    type: "datetime"
                                }, {
                                    name: "pay_before",
                                    type: "datetime"
                                }, {
                                    name: "is_paid",
                                    type: "boolean"
                                }, {
                                    name: "confidentiality",
                                    type: "string",
                                    required: true,
                                    _embedded: {
                                        "blueprint:constraint": [{
                                            type: "required"
                                        }, {
                                            type: "allowed-values",
                                            values: ["confidential", "public", "secret"]
                                        }],
                                        "blueprint:search-param": [{
                                            name: "confidentiality",
                                            type: "exact"
                                        }]
                                    }
                                }, {
                                    name: "content",
                                    type: "object",
                                    _embedded: {
                                        "blueprint:attribute": [{
                                            name: "filename",
                                            type: "string"
                                        }, {
                                            name: "mimetype",
                                            type: "string"
                                        }, {
                                            name: "length",
                                            type: "long",
                                            readOnly: true
                                        }]
                                    }
                                }, {
                                    name: "audit_metadata",
                                    type: "object",
                                    _embedded: {
                                        "blueprint:attribute": [{
                                            name: "created_date",
                                            type: "datetime",
                                            readOnly: true
                                        }, {
                                            name: "created_by",
                                            type: "string",
                                            readOnly: true
                                        }, {
                                            name: "last_modified_date",
                                            type: "datetime",
                                            readOnly: true
                                        }, {
                                            name: "last_modified_by",
                                            type: "string",
                                            readOnly: true
                                        }]
                                    }
                                }],
                                "blueprint:relation": [{
                                    name: "customer",
                                    many_source_per_target: true,
                                    many_target_per_source: false,
                                    required: true,
                                    _links: {
                                        "blueprint:target-entity": {
                                            href: "http://localhost/profile/persons"
                                        }
                                    }
                                }, {
                                    name: "products",
                                    many_source_per_target: true,
                                    many_target_per_source: true,
                                    _links: {
                                        "blueprint:target-entity": {
                                            href: "http://localhost/profile/products"
                                        }
                                    }
                                }, {
                                    name: "previous_invoice",
                                    many_source_per_target: false,
                                    many_target_per_source: false,
                                    _links: {
                                        "blueprint:target-entity": {
                                            href: "http://localhost/profile/invoices"
                                        }
                                    }
                                }, {
                                    name: "next_invoice",
                                    many_source_per_target: false,
                                    many_target_per_source: false,
                                    _links: {
                                        "blueprint:target-entity": {
                                            href: "http://localhost/profile/invoices"
                                        }
                                    }
                                }]
                            }
                        }
                        """))
                .andExpect(jsonPath("$._embedded.blueprint:attribute[?(@.name=='_version')]").doesNotExist());
    }

    @Test
    void getProfileEntity_linksAndTemplates() throws Exception {
        mockMvc.perform(get("/profile/invoices").accept(MediaTypes.HAL_FORMS_JSON))
                .andExpect(status().isOk())
                // TODO: remove '?page=0' in all links (ACC-2200)
                .andExpect(content().json("""
                        {
                            _links: {
                                self: {
                                    href: "http://localhost/profile/invoices"
                                },
                                describes: [{
                                    href: "http://localhost/invoices?page=0",
                                    name: "collection"
                                }, {
                                    href: "http://localhost/invoices/{instanceId}",
                                    templated: true,
                                    name: "item"
                                }]
                            },
                            _templates: {
                                default: {
                                    method: "HEAD",
                                    target: "http://localhost/invoices?page=0"
                                },
                                search: {
                                    method: "GET",
                                    target: "http://localhost/invoices?page=0",
                                    properties: [{
                                        name: "number",
                                        type: "text"
                                    }, {
                                        name: "confidentiality",
                                        type: "text",
                                        options: {
                                            inline: ["confidential", "public", "secret"]
                                        }
                                    }, {
                                        name: "customer.name~prefix",
                                        type: "text"
                                    }, {
                                        name: "customer.vat",
                                        type: "text"
                                    }, {
                                        name: "products.code",
                                        type: "text"
                                    }, {
                                        name: "previous_invoice.number",
                                        type: "text"
                                    }, {
                                        name: "previous_invoice.confidentiality",
                                        type: "text",
                                        options: {
                                            inline: ["confidential", "public", "secret"]
                                        }
                                    }, {
                                        name: "next_invoice.number",
                                        type: "text"
                                    }, {
                                        name: "next_invoice.confidentiality",
                                        type: "text",
                                        options: {
                                            inline: ["confidential", "public", "secret"]
                                        }
                                    }, {
                                        name: "_sort",
                                        type: "text",
                                        options: {
                                            inline: [{
                                                value: "number,asc",
                                                property: "number",
                                                direction: "asc"
                                            }, {
                                                value: "number,desc",
                                                property: "number",
                                                direction: "desc"
                                            }, {
                                                value: "amount,asc",
                                                property: "amount",
                                                direction: "asc"
                                            }, {
                                                value: "amount,desc",
                                                property: "amount",
                                                direction: "desc"
                                            }, {
                                                value: "confidentiality,asc",
                                                property: "confidentiality",
                                                direction: "asc"
                                            }, {
                                                value: "confidentiality,desc",
                                                property: "confidentiality",
                                                direction: "desc"
                                            }]
                                        }
                                    }]
                                },
                                create-form: {
                                    method: "POST",
                                    target: "http://localhost/invoices?page=0",
                                    contentType: "multipart/form-data",
                                    properties: [{
                                        name: "number",
                                        required: true,
                                        type: "text"
                                    }, {
                                        name: "amount",
                                        required: true,
                                        type: "number"
                                    }, {
                                        name: "received",
                                        type: "datetime"
                                    }, {
                                        name: "pay_before",
                                        type: "datetime"
                                    }, {
                                        name: "is_paid",
                                        type: "checkbox"
                                    }, {
                                        name: "confidentiality",
                                        required: true,
                                        type: "text",
                                        options: {
                                            inline: ["confidential", "public", "secret"],
                                            minItems: 1,
                                            maxItems: 1
                                        }
                                    }, {
                                        name: "content",
                                        type: "file"
                                    }, {
                                        name: "customer",
                                        required: true,
                                        type: "url",
                                        options: {
                                            link: {
                                                href: "http://localhost/persons?page=0"
                                            },
                                            minItems: 1,
                                            maxItems: 1
                                        }
                                    }, {
                                        name: "products",
                                        type: "url",
                                        options: {
                                            link: {
                                                href: "http://localhost/products?page=0"
                                            },
                                            minItems: 0
                                        }
                                    }, {
                                        name: "previous_invoice",
                                        type: "url",
                                        options: {
                                            link: {
                                                href: "http://localhost/invoices?page=0"
                                            },
                                            minItems: 0,
                                            maxItems: 1
                                        }
                                    }, {
                                        name: "next_invoice",
                                        type: "url",
                                        options: {
                                            link: {
                                                href: "http://localhost/invoices?page=0"
                                            },
                                            minItems: 0,
                                            maxItems: 1
                                        }
                                    }]
                                }
                            }
                        }
                        """));
    }

    @Test
    void getProfileEntity_withoutContent() throws Exception {
        mockMvc.perform(get("/profile/persons").accept(MediaTypes.HAL_FORMS_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._templates.create-form.contentType").value("application/json"));
    }

    @Test
    void getProfileEntity_notFound() throws Exception {
        mockMvc.perform(get("/profile/not-found").accept(MediaTypes.HAL_FORMS_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProfileEntity_jsonSchema() throws Exception {
        mockMvc.perform(get("/profile/invoices").accept("application/schema+json"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                            title: "invoice",
                            properties: {
                                id: {
                                    type: "string",
                                    format: "uuid",
                                    readOnly: true
                                },
                                number: {
                                    type: "string"
                                },
                                amount: {
                                    type: "number"
                                },
                                received: {
                                    type: "string",
                                    format: "date-time"
                                },
                                pay_before: {
                                    type: "string",
                                    format: "date-time"
                                },
                                is_paid: {
                                    type: boolean
                                },
                                confidentiality: {
                                    enum: ["confidential", "public", "secret"]
                                },
                                content: {
                                    $ref: "#/$defs/content"
                                },
                                audit_metadata: {
                                    type: "object",
                                    properties: {
                                        created_date: {
                                            type: "string",
                                            format: "date-time",
                                            readOnly: true
                                        },
                                        created_by: {
                                            type: "string",
                                            readOnly: true
                                        },
                                        last_modified_date: {
                                            type: "string",
                                            format: "date-time",
                                            readOnly: true
                                        },
                                        last_modified_by: {
                                            type: "string",
                                            readOnly: true
                                        }
                                    }
                                },
                                customer: {
                                    type: "string",
                                    format: "uri"
                                },
                                previous_invoice: {
                                    type: "string",
                                    format: "uri"
                                },
                                next_invoice: {
                                    type: "string",
                                    format: "uri"
                                },
                                products: {
                                    items: {
                                        type: "string",
                                        format: "uri"
                                    },
                                    uniqueItems: true
                                }
                            },
                            required: ["number", "amount", "confidentiality", "customer"],
                            $defs: {
                                content: {
                                    type: "object",
                                    properties: {
                                        filename: {
                                            type: "string"
                                        },
                                        mimetype: {
                                            type: "string"
                                        },
                                        length: {
                                            type: "integer",
                                            readOnly: true
                                        }
                                    }
                                }
                            },
                            $schema: "https://json-schema.org/draft/2020-12/schema"
                        }
                        """))
                .andExpect(jsonPath("$.properties._version").doesNotExist());
    }

}