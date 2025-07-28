package com.contentgrid.appserver.rest.hal.forms;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.searchfilters.PrefixSearchFilter;
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
import com.contentgrid.appserver.rest.EntityRestController;
import com.contentgrid.appserver.rest.TestApplication;
import com.contentgrid.appserver.rest.hal.forms.property.PropertyMetadataWithOptions;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.hateoas.AffordanceModel.InputPayloadMetadata;
import org.springframework.hateoas.mediatype.hal.forms.HalFormsOptions;
import org.springframework.hateoas.mediatype.html.HtmlInputType;
import org.springframework.http.MediaType;

class HalFormsPayloadMetadataConverterTest {

    HalFormsPayloadMetadataConverter converter = new HalFormsPayloadMetadataConverter(
            List.of(new HalFormsPayloadMetadataContributor()));

    @Test
    void convertToCreatePayloadMetadata() {
        var metadata = converter.convertToCreatePayloadMetadata(TestApplication.APPLICATION, TestApplication.INVOICE);

        assertThat(metadata.getType()).isEqualTo(Object.class);

        assertThat(metadata.stream()).satisfiesExactlyInAnyOrder(
                number -> {
                    assertThat(number.getName()).isEqualTo("number");
                    assertThat(number.isReadOnly()).isFalse();
                    assertThat(number.isRequired()).isTrue();
                    assertThat(number.getInputType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                },
                amount -> {
                    assertThat(amount.getName()).isEqualTo("amount");
                    assertThat(amount.isReadOnly()).isFalse();
                    assertThat(amount.isRequired()).isTrue();
                    assertThat(amount.getInputType()).isEqualTo(HtmlInputType.NUMBER_VALUE);
                },
                received -> {
                    assertThat(received.getName()).isEqualTo("received");
                    assertThat(received.isReadOnly()).isFalse();
                    assertThat(received.isRequired()).isFalse();
                    assertThat(received.getInputType()).isEqualTo("datetime");
                },
                payBefore -> {
                    assertThat(payBefore.getName()).isEqualTo("pay_before");
                    assertThat(payBefore.isReadOnly()).isFalse();
                    assertThat(payBefore.isRequired()).isFalse();
                    assertThat(payBefore.getInputType()).isEqualTo("datetime");
                },
                paid -> {
                    assertThat(paid.getName()).isEqualTo("is_paid");
                    assertThat(paid.isReadOnly()).isFalse();
                    assertThat(paid.isRequired()).isFalse();
                    assertThat(paid.getInputType()).isEqualTo(HtmlInputType.CHECKBOX_VALUE);
                },
                confidentiality -> {
                    assertThat(confidentiality.getName()).isEqualTo("confidentiality");
                    assertThat(confidentiality.isReadOnly()).isFalse();
                    assertThat(confidentiality.isRequired()).isTrue();
                    assertThat(confidentiality.getInputType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                    assertThat(confidentiality).isInstanceOfSatisfying(PropertyMetadataWithOptions.class, propertyMetadata -> {
                        assertThat(propertyMetadata.getOptions()).isInstanceOfSatisfying(HalFormsOptions.Inline.class, options -> {
                            assertThat(options.getInline()).satisfiesExactlyInAnyOrder(
                                    value1 -> assertThat(value1).isEqualTo("public"),
                                    value2 -> assertThat(value2).isEqualTo("confidential"),
                                    value3 -> assertThat(value3).isEqualTo("secret")
                            );
                            assertThat(options.getMinItems()).isEqualTo(1L);
                            assertThat(options.getMaxItems()).isEqualTo(1L);
                        });
                    });
                },
                content -> {
                    assertThat(content.getName()).isEqualTo("content");
                    assertThat(content.isReadOnly()).isFalse();
                    assertThat(content.isRequired()).isFalse();
                    assertThat(content.getInputType()).isEqualTo(HtmlInputType.FILE_VALUE);
                },
                customer -> {
                    assertThat(customer.getName()).isEqualTo("customer");
                    assertThat(customer.isReadOnly()).isFalse();
                    assertThat(customer.isRequired()).isTrue();
                    assertThat(customer.getInputType()).isEqualTo(HtmlInputType.URL_VALUE);
                    assertThat(customer).isInstanceOfSatisfying(PropertyMetadataWithOptions.class, propertyMetadata -> {
                        assertThat(propertyMetadata.getOptions()).isInstanceOfSatisfying(HalFormsOptions.Remote.class, options -> {
                            assertThat(options.getLink().getHref()).isEqualTo("/persons?page=0");
                            assertThat(options.getMinItems()).isEqualTo(1L);
                            assertThat(options.getMaxItems()).isEqualTo(1L);
                        });
                    });
                },
                products -> {
                    assertThat(products.getName()).isEqualTo("products");
                    assertThat(products.isReadOnly()).isFalse();
                    assertThat(products.isRequired()).isFalse();
                    assertThat(products.getInputType()).isEqualTo(HtmlInputType.URL_VALUE);
                    assertThat(products).isInstanceOfSatisfying(PropertyMetadataWithOptions.class, propertyMetadata -> {
                        assertThat(propertyMetadata.getOptions()).isInstanceOfSatisfying(HalFormsOptions.Remote.class, options -> {
                            assertThat(options.getLink().getHref()).isEqualTo("/products?page=0");
                            assertThat(options.getMinItems()).isEqualTo(0L);
                            assertThat(options.getMaxItems()).isNull();
                        });
                    });
                },
                previous -> {
                    assertThat(previous.getName()).isEqualTo("previous_invoice");
                    assertThat(previous.isReadOnly()).isFalse();
                    assertThat(previous.isRequired()).isFalse();
                    assertThat(previous.getInputType()).isEqualTo(HtmlInputType.URL_VALUE);
                    assertThat(previous).isInstanceOfSatisfying(PropertyMetadataWithOptions.class, propertyMetadata -> {
                        assertThat(propertyMetadata.getOptions()).isInstanceOfSatisfying(HalFormsOptions.Remote.class, options -> {
                            assertThat(options.getLink().getHref()).isEqualTo("/invoices?page=0");
                            assertThat(options.getMinItems()).isEqualTo(0L);
                            assertThat(options.getMaxItems()).isEqualTo(1L);
                        });
                    });
                },
                next -> {
                    assertThat(next.getName()).isEqualTo("next_invoice");
                    assertThat(next.isReadOnly()).isFalse();
                    assertThat(next.isRequired()).isFalse();
                    assertThat(next.getInputType()).isEqualTo(HtmlInputType.URL_VALUE);
                    assertThat(next).isInstanceOfSatisfying(PropertyMetadataWithOptions.class, propertyMetadata -> {
                        assertThat(propertyMetadata.getOptions()).isInstanceOfSatisfying(HalFormsOptions.Remote.class, options -> {
                            assertThat(options.getLink().getHref()).isEqualTo("/invoices?page=0");
                            assertThat(options.getMinItems()).isEqualTo(0L);
                            assertThat(options.getMaxItems()).isEqualTo(1L);
                        });
                    });
                }
        );

        assertThat(InputPayloadMetadata.from(metadata).getMediaTypes()).containsExactly(MediaType.MULTIPART_FORM_DATA);
    }

    @Test
    void convertToCreatePayloadMetadata_noContent() {
        var metadata = converter.convertToCreatePayloadMetadata(TestApplication.APPLICATION, TestApplication.PERSON);

        assertThat(metadata.getType()).isEqualTo(Object.class);

        assertThat(metadata.stream()).satisfiesExactlyInAnyOrder(
                name -> {
                    assertThat(name.getName()).isEqualTo("name");
                    assertThat(name.isReadOnly()).isFalse();
                    assertThat(name.isRequired()).isTrue();
                    assertThat(name.getInputType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                },
                vat -> {
                    assertThat(vat.getName()).isEqualTo("vat");
                    assertThat(vat.isReadOnly()).isFalse();
                    assertThat(vat.isRequired()).isTrue();
                    assertThat(vat.getInputType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                },
                gender -> {
                    assertThat(gender.getName()).isEqualTo("gender");
                    assertThat(gender.isReadOnly()).isFalse();
                    assertThat(gender.isRequired()).isFalse();
                    assertThat(gender.getInputType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                    assertThat(gender).isInstanceOfSatisfying(PropertyMetadataWithOptions.class, propertyMetadata -> {
                        assertThat(propertyMetadata.getOptions()).isInstanceOfSatisfying(HalFormsOptions.Inline.class, options -> {
                            assertThat(options.getInline()).satisfiesExactlyInAnyOrder(
                                    value1 -> assertThat(value1).isEqualTo("female"),
                                    value2 -> assertThat(value2).isEqualTo("male")
                            );
                            assertThat(options.getMinItems()).isEqualTo(0L);
                            assertThat(options.getMaxItems()).isEqualTo(1L);
                        });
                    });
                },
                invoices -> {
                    assertThat(invoices.getName()).isEqualTo("invoices");
                    assertThat(invoices.isReadOnly()).isFalse();
                    assertThat(invoices.isRequired()).isFalse();
                    assertThat(invoices.getInputType()).isEqualTo(HtmlInputType.URL_VALUE);
                    assertThat(invoices).isInstanceOfSatisfying(PropertyMetadataWithOptions.class, propertyMetadata -> {
                        assertThat(propertyMetadata.getOptions()).isInstanceOfSatisfying(HalFormsOptions.Remote.class, options -> {
                            assertThat(options.getLink().getHref()).isEqualTo("/invoices?page=0");
                            assertThat(options.getMinItems()).isEqualTo(0L);
                            assertThat(options.getMaxItems()).isNull();
                        });
                    });
                },
                friends -> {
                    assertThat(friends.getName()).isEqualTo("friends");
                    assertThat(friends.isReadOnly()).isFalse();
                    assertThat(friends.isRequired()).isFalse();
                    assertThat(friends.getInputType()).isEqualTo(HtmlInputType.URL_VALUE);
                    assertThat(friends).isInstanceOfSatisfying(PropertyMetadataWithOptions.class, propertyMetadata -> {
                        assertThat(propertyMetadata.getOptions()).isInstanceOfSatisfying(HalFormsOptions.Remote.class, options -> {
                            assertThat(options.getLink().getHref()).isEqualTo("/persons?page=0");
                            assertThat(options.getMinItems()).isEqualTo(0L);
                            assertThat(options.getMaxItems()).isNull();
                        });
                    });
                }
        );

        // No File input; use JSON for mediatype
        assertThat(InputPayloadMetadata.from(metadata).getMediaTypes()).containsExactly(MediaType.APPLICATION_JSON);
    }

    @Test
    void convertToUpdatePayloadMetadata() {
        var metadata = converter.convertToUpdatePayloadMetadata(TestApplication.APPLICATION, TestApplication.INVOICE);

        assertThat(metadata.getType()).isEqualTo(Object.class);

        assertThat(metadata.stream()).satisfiesExactlyInAnyOrder(
                number -> {
                    assertThat(number.getName()).isEqualTo("number");
                    assertThat(number.isReadOnly()).isFalse();
                    assertThat(number.isRequired()).isTrue();
                    assertThat(number.getInputType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                },
                amount -> {
                    assertThat(amount.getName()).isEqualTo("amount");
                    assertThat(amount.isReadOnly()).isFalse();
                    assertThat(amount.isRequired()).isTrue();
                    assertThat(amount.getInputType()).isEqualTo(HtmlInputType.NUMBER_VALUE);
                },
                received -> {
                    assertThat(received.getName()).isEqualTo("received");
                    assertThat(received.isReadOnly()).isFalse();
                    assertThat(received.isRequired()).isFalse();
                    assertThat(received.getInputType()).isEqualTo("datetime");
                },
                payBefore -> {
                    assertThat(payBefore.getName()).isEqualTo("pay_before");
                    assertThat(payBefore.isReadOnly()).isFalse();
                    assertThat(payBefore.isRequired()).isFalse();
                    assertThat(payBefore.getInputType()).isEqualTo("datetime");
                },
                paid -> {
                    assertThat(paid.getName()).isEqualTo("is_paid");
                    assertThat(paid.isReadOnly()).isFalse();
                    assertThat(paid.isRequired()).isFalse();
                    assertThat(paid.getInputType()).isEqualTo(HtmlInputType.CHECKBOX_VALUE);
                },
                confidentiality -> {
                    assertThat(confidentiality.getName()).isEqualTo("confidentiality");
                    assertThat(confidentiality.isReadOnly()).isFalse();
                    assertThat(confidentiality.isRequired()).isTrue();
                    assertThat(confidentiality.getInputType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                    assertThat(confidentiality).isInstanceOfSatisfying(PropertyMetadataWithOptions.class, propertyMetadata -> {
                        assertThat(propertyMetadata.getOptions()).isInstanceOfSatisfying(HalFormsOptions.Inline.class, options -> {
                            assertThat(options.getInline()).satisfiesExactlyInAnyOrder(
                                    value1 -> assertThat(value1).isEqualTo("public"),
                                    value2 -> assertThat(value2).isEqualTo("confidential"),
                                    value3 -> assertThat(value3).isEqualTo("secret")
                            );
                            assertThat(options.getMinItems()).isEqualTo(1L);
                            assertThat(options.getMaxItems()).isEqualTo(1L);
                        });
                    });
                },
                contentFilename -> {
                    assertThat(contentFilename.getName()).isEqualTo("content.filename");
                    assertThat(contentFilename.isReadOnly()).isFalse();
                    assertThat(contentFilename.isRequired()).isFalse();
                    assertThat(contentFilename.getInputType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                },
                contentMimetype -> {
                    assertThat(contentMimetype.getName()).isEqualTo("content.mimetype");
                    assertThat(contentMimetype.isReadOnly()).isFalse();
                    assertThat(contentMimetype.isRequired()).isFalse();
                    assertThat(contentMimetype.getInputType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                }
        );

        assertThat(InputPayloadMetadata.from(metadata).getMediaTypes()).containsExactly(MediaType.APPLICATION_JSON);
    }

    @Test
    void convertToSearchPayloadMetadata() {
        var metadata = converter.convertToSearchPayloadMetadata(TestApplication.APPLICATION, TestApplication.INVOICE);

        assertThat(metadata.getType()).isEqualTo(Object.class);

        assertThat(metadata.stream()).satisfiesExactlyInAnyOrder(
                number -> {
                    assertThat(number.getName()).isEqualTo("number");
                    assertThat(number.isReadOnly()).isFalse();
                    assertThat(number.isRequired()).isFalse();
                    assertThat(number.getInputType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                },
                confidentiality -> {
                    assertThat(confidentiality.getName()).isEqualTo("confidentiality");
                    assertThat(confidentiality.isReadOnly()).isFalse();
                    assertThat(confidentiality.isRequired()).isFalse();
                    assertThat(confidentiality.getInputType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                    assertThat(confidentiality).isInstanceOfSatisfying(PropertyMetadataWithOptions.class, propertyMetadata -> {
                        assertThat(propertyMetadata.getOptions()).isInstanceOfSatisfying(HalFormsOptions.Inline.class, options -> {
                            assertThat(options.getInline()).satisfiesExactlyInAnyOrder(
                                    value1 -> assertThat(value1).isEqualTo("public"),
                                    value2 -> assertThat(value2).isEqualTo("confidential"),
                                    value3 -> assertThat(value3).isEqualTo("secret")
                            );
                            assertThat(options.getMinItems()).isEqualTo(0L);
                            assertThat(options.getMaxItems()).isNull();
                        });
                    });
                },
                customerName -> {
                    assertThat(customerName.getName()).isEqualTo("customer.name~prefix");
                    assertThat(customerName.isReadOnly()).isFalse();
                    assertThat(customerName.isRequired()).isFalse();
                    assertThat(customerName.getInputType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                },
                customerVat -> {
                    assertThat(customerVat.getName()).isEqualTo("customer.vat");
                    assertThat(customerVat.isReadOnly()).isFalse();
                    assertThat(customerVat.isRequired()).isFalse();
                    assertThat(customerVat.getInputType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                },
                productsName -> {
                    assertThat(productsName.getName()).isEqualTo("products.code");
                    assertThat(productsName.isReadOnly()).isFalse();
                    assertThat(productsName.isRequired()).isFalse();
                    assertThat(productsName.getInputType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                },
                previousNumber -> {
                    assertThat(previousNumber.getName()).isEqualTo("previous_invoice.number");
                    assertThat(previousNumber.isReadOnly()).isFalse();
                    assertThat(previousNumber.isRequired()).isFalse();
                    assertThat(previousNumber.getInputType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                },
                previousConfidentiality -> {
                    assertThat(previousConfidentiality.getName()).isEqualTo("previous_invoice.confidentiality");
                    assertThat(previousConfidentiality.isReadOnly()).isFalse();
                    assertThat(previousConfidentiality.isRequired()).isFalse();
                    assertThat(previousConfidentiality.getInputType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                    assertThat(previousConfidentiality).isInstanceOfSatisfying(PropertyMetadataWithOptions.class, propertyMetadata -> {
                        assertThat(propertyMetadata.getOptions()).isInstanceOfSatisfying(HalFormsOptions.Inline.class, options -> {
                            assertThat(options.getInline()).satisfiesExactlyInAnyOrder(
                                    value1 -> assertThat(value1).isEqualTo("public"),
                                    value2 -> assertThat(value2).isEqualTo("confidential"),
                                    value3 -> assertThat(value3).isEqualTo("secret")
                            );
                            assertThat(options.getMinItems()).isEqualTo(0L);
                            assertThat(options.getMaxItems()).isNull();
                        });
                    });
                },
                nextNumber -> {
                    assertThat(nextNumber.getName()).isEqualTo("next_invoice.number");
                    assertThat(nextNumber.isReadOnly()).isFalse();
                    assertThat(nextNumber.isRequired()).isFalse();
                    assertThat(nextNumber.getInputType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                },
                nextConfidentiality -> {
                    assertThat(nextConfidentiality.getName()).isEqualTo("next_invoice.confidentiality");
                    assertThat(nextConfidentiality.isReadOnly()).isFalse();
                    assertThat(nextConfidentiality.isRequired()).isFalse();
                    assertThat(nextConfidentiality.getInputType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                    assertThat(nextConfidentiality).isInstanceOfSatisfying(PropertyMetadataWithOptions.class, propertyMetadata -> {
                        assertThat(propertyMetadata.getOptions()).isInstanceOfSatisfying(HalFormsOptions.Inline.class, options -> {
                            assertThat(options.getInline()).satisfiesExactlyInAnyOrder(
                                    value1 -> assertThat(value1).isEqualTo("public"),
                                    value2 -> assertThat(value2).isEqualTo("confidential"),
                                    value3 -> assertThat(value3).isEqualTo("secret")
                            );
                            assertThat(options.getMinItems()).isEqualTo(0L);
                            assertThat(options.getMaxItems()).isNull();
                        });
                    });
                },
                sort -> {
                    assertThat(sort.getName()).isEqualTo(EntityRestController.SORT_NAME);
                    assertThat(sort.isReadOnly()).isFalse();
                    assertThat(sort.isRequired()).isFalse();
                    assertThat(sort.getInputType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                    assertThat(sort).isInstanceOfSatisfying(PropertyMetadataWithOptions.class, propertyMetadata -> {
                        assertThat(propertyMetadata.getOptions()).isInstanceOfSatisfying(HalFormsOptions.Inline.class, options -> {
                            assertThat(options.getInline()).satisfiesExactlyInAnyOrder(
                                    numberAsc -> assertThat(numberAsc.toString())
                                            .contains("value=number,asc")
                                            .contains("property=number")
                                            .contains("direction=asc"),
                                    numberDesc -> assertThat(numberDesc.toString())
                                            .contains("value=number,desc")
                                            .contains("property=number")
                                            .contains("direction=desc"),
                                    confidentialityAsc -> assertThat(confidentialityAsc.toString())
                                            .contains("value=confidentiality,asc")
                                            .contains("property=confidentiality")
                                            .contains("direction=asc"),
                                    confidentialityDesc -> assertThat(confidentialityDesc.toString())
                                            .contains("value=confidentiality,desc")
                                            .contains("property=confidentiality")
                                            .contains("direction=desc")
                            );
                            assertThat(options.getMinItems()).isEqualTo(0L);
                            assertThat(options.getMaxItems()).isNull();
                        });
                    });
                }
        );

        assertThat(InputPayloadMetadata.from(metadata).getMediaTypes()).isEmpty();
    }

    @Test
    void convertToSearchPayloadMetadata_toManyRelations() {
        var metadata = converter.convertToSearchPayloadMetadata(TestApplication.APPLICATION, TestApplication.PERSON);

        assertThat(metadata.getType()).isEqualTo(Object.class);

        assertThat(metadata.stream()).satisfiesExactlyInAnyOrder(
                name -> {
                    assertThat(name.getName()).isEqualTo("name~prefix");
                    assertThat(name.isReadOnly()).isFalse();
                    assertThat(name.isRequired()).isFalse();
                    assertThat(name.getInputType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                },
                vat -> {
                    assertThat(vat.getName()).isEqualTo("vat");
                    assertThat(vat.isReadOnly()).isFalse();
                    assertThat(vat.isRequired()).isFalse();
                    assertThat(vat.getInputType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                },
                friendsName -> {
                    assertThat(friendsName.getName()).isEqualTo("friends.name~prefix");
                    assertThat(friendsName.isReadOnly()).isFalse();
                    assertThat(friendsName.isRequired()).isFalse();
                    assertThat(friendsName.getInputType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                },
                friendsVat -> {
                    assertThat(friendsVat.getName()).isEqualTo("friends.vat");
                    assertThat(friendsVat.isReadOnly()).isFalse();
                    assertThat(friendsVat.isRequired()).isFalse();
                    assertThat(friendsVat.getInputType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                },
                invoicesNumber -> {
                    assertThat(invoicesNumber.getName()).isEqualTo("invoices.number");
                    assertThat(invoicesNumber.isReadOnly()).isFalse();
                    assertThat(invoicesNumber.isRequired()).isFalse();
                    assertThat(invoicesNumber.getInputType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                },
                invoicesConfidentiality -> {
                    assertThat(invoicesConfidentiality.getName()).isEqualTo("invoices.confidentiality");
                    assertThat(invoicesConfidentiality.isReadOnly()).isFalse();
                    assertThat(invoicesConfidentiality.isRequired()).isFalse();
                    assertThat(invoicesConfidentiality.getInputType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                    assertThat(invoicesConfidentiality).isInstanceOfSatisfying(PropertyMetadataWithOptions.class, propertyMetadata -> {
                        assertThat(propertyMetadata.getOptions()).isInstanceOfSatisfying(HalFormsOptions.Inline.class, options -> {
                            assertThat(options.getInline()).satisfiesExactlyInAnyOrder(
                                    value1 -> assertThat(value1).isEqualTo("public"),
                                    value2 -> assertThat(value2).isEqualTo("confidential"),
                                    value3 -> assertThat(value3).isEqualTo("secret")
                            );
                            assertThat(options.getMinItems()).isEqualTo(0L);
                            assertThat(options.getMaxItems()).isNull();
                        });
                    });
                },
                sort -> {
                    assertThat(sort.getName()).isEqualTo(EntityRestController.SORT_NAME);
                    assertThat(sort.isReadOnly()).isFalse();
                    assertThat(sort.isRequired()).isFalse();
                    assertThat(sort.getInputType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                    assertThat(sort).isInstanceOfSatisfying(PropertyMetadataWithOptions.class, propertyMetadata -> {
                        assertThat(propertyMetadata.getOptions()).isInstanceOfSatisfying(HalFormsOptions.Inline.class, options -> {
                            assertThat(options.getInline()).satisfiesExactlyInAnyOrder(
                                    vatAsc -> assertThat(vatAsc.toString())
                                            .contains("value=vat,asc")
                                            .contains("property=vat")
                                            .contains("direction=asc"),
                                    vatDesc -> assertThat(vatDesc.toString())
                                            .contains("value=vat,desc")
                                            .contains("property=vat")
                                            .contains("direction=desc"),
                                    nameAsc -> assertThat(nameAsc.toString())
                                            .contains("value=name,asc")
                                            .contains("property=name")
                                            .contains("direction=asc"),
                                    nameDesc -> assertThat(nameDesc.toString())
                                            .contains("value=name,desc")
                                            .contains("property=name")
                                            .contains("direction=desc")
                            );
                            assertThat(options.getMinItems()).isEqualTo(0L);
                            assertThat(options.getMaxItems()).isNull();
                        });
                    });
                }
        );

        assertThat(InputPayloadMetadata.from(metadata).getMediaTypes()).isEmpty();
    }

    @Test
    void convertToSearchPayloadMetadata_noSortableFields() {
        var entity = Entity.builder()
                .name(EntityName.of("test"))
                .table(TableName.of("test"))
                .pathSegment(PathSegmentName.of("test"))
                .linkName(LinkName.of("test"))
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("one"))
                        .column(ColumnName.of("one"))
                        .type(Type.TEXT)
                        .build())
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("two"))
                        .column(ColumnName.of("two"))
                        .type(Type.TEXT)
                        .build())
                .searchFilter(PrefixSearchFilter.builder()
                        .name(FilterName.of("one~prefix"))
                        .attributePath(PropertyPath.of(AttributeName.of("one")))
                        .attributeType(Type.TEXT)
                        .build())
                .build();
        var application = Application.builder()
                .name(ApplicationName.of("test-application"))
                .entity(entity)
                .build();
        var metadata = converter.convertToSearchPayloadMetadata(application, entity);

        assertThat(metadata.getType()).isEqualTo(Object.class);

        // _sort is not present
        assertThat(metadata.stream()).singleElement().satisfies(filter -> {
            assertThat(filter.getName()).isEqualTo("one~prefix");
            assertThat(filter.getInputType()).isEqualTo(HtmlInputType.TEXT_VALUE);
        });
    }

    @Test
    void convertToSearchPayloadMetadata_noSearchFilters() {
        var entity = Entity.builder()
                .name(EntityName.of("test"))
                .table(TableName.of("test"))
                .pathSegment(PathSegmentName.of("test"))
                .linkName(LinkName.of("test"))
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("one"))
                        .column(ColumnName.of("one"))
                        .type(Type.TEXT)
                        .build())
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("two"))
                        .column(ColumnName.of("two"))
                        .type(Type.TEXT)
                        .build())
                .sortableField(SortableField.builder()
                        .name(SortableName.of("one"))
                        .propertyPath(PropertyPath.of(AttributeName.of("one")))
                        .build())
                .build();
        var application = Application.builder()
                .name(ApplicationName.of("test-application"))
                .entity(entity)
                .build();
        var metadata = converter.convertToSearchPayloadMetadata(application, entity);

        assertThat(metadata.getType()).isEqualTo(Object.class);

        // only _sort is present
        assertThat(metadata.stream()).singleElement().satisfies(sort -> {
            assertThat(sort.getName()).isEqualTo(EntityRestController.SORT_NAME);
            assertThat(sort.getInputType()).isEqualTo(HtmlInputType.TEXT_VALUE);
        });
    }
}
