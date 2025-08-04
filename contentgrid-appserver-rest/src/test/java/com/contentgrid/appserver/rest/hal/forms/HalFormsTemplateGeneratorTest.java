package com.contentgrid.appserver.rest.hal.forms;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures;
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
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.rest.EntityRestController;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.hateoas.mediatype.hal.forms.HalFormsOptions;
import org.springframework.hateoas.mediatype.hal.forms.HalFormsOptions.Inline;
import org.springframework.hateoas.mediatype.hal.forms.HalFormsOptions.Remote;
import org.springframework.hateoas.mediatype.html.HtmlInputType;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

class HalFormsTemplateGeneratorTest {

    private static final EntityId ENTITY_ID = EntityId.of(UUID.randomUUID());

    HalFormsTemplateGenerator generator = new HalFormsTemplateGenerator(new HalFormsPropertyContributor());

    @Test
    void generateCreateTemplate() {
        var template = generator.generateCreateTemplate(ModelTestFixtures.APPLICATION, ModelTestFixtures.INVOICE);

        assertThat(template.getProperties()).satisfiesExactlyInAnyOrder(
                number -> {
                    assertThat(number.getName()).isEqualTo("number");
                    assertThat(number.isReadOnly()).isFalse();
                    assertThat(number.isRequired()).isTrue();
                    assertThat(number.getType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                },
                amount -> {
                    assertThat(amount.getName()).isEqualTo("amount");
                    assertThat(amount.isReadOnly()).isFalse();
                    assertThat(amount.isRequired()).isTrue();
                    assertThat(amount.getType()).isEqualTo(HtmlInputType.NUMBER_VALUE);
                },
                received -> {
                    assertThat(received.getName()).isEqualTo("received");
                    assertThat(received.isReadOnly()).isFalse();
                    assertThat(received.isRequired()).isFalse();
                    assertThat(received.getType()).isEqualTo("datetime");
                },
                payBefore -> {
                    assertThat(payBefore.getName()).isEqualTo("pay_before");
                    assertThat(payBefore.isReadOnly()).isFalse();
                    assertThat(payBefore.isRequired()).isFalse();
                    assertThat(payBefore.getType()).isEqualTo("datetime");
                },
                paid -> {
                    assertThat(paid.getName()).isEqualTo("is_paid");
                    assertThat(paid.isReadOnly()).isFalse();
                    assertThat(paid.isRequired()).isFalse();
                    assertThat(paid.getType()).isEqualTo(HtmlInputType.CHECKBOX_VALUE);
                },
                confidentiality -> {
                    assertThat(confidentiality.getName()).isEqualTo("confidentiality");
                    assertThat(confidentiality.isReadOnly()).isFalse();
                    assertThat(confidentiality.isRequired()).isTrue();
                    assertThat(confidentiality.getType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                    assertThat(confidentiality.getOptions()).isInstanceOfSatisfying(HalFormsOptions.Inline.class, options -> {
                        assertThat(options.getInline()).satisfiesExactlyInAnyOrder(
                                value1 -> assertThat(value1).isEqualTo("public"),
                                value2 -> assertThat(value2).isEqualTo("confidential"),
                                value3 -> assertThat(value3).isEqualTo("secret")
                        );
                        assertThat(options.getMinItems()).isOne();
                        assertThat(options.getMaxItems()).isOne();
                    });
                },
                content -> {
                    assertThat(content.getName()).isEqualTo("content");
                    assertThat(content.isReadOnly()).isFalse();
                    assertThat(content.isRequired()).isFalse();
                    assertThat(content.getType()).isEqualTo(HtmlInputType.FILE_VALUE);
                },
                customer -> {
                    assertThat(customer.getName()).isEqualTo("customer");
                    assertThat(customer.isReadOnly()).isFalse();
                    assertThat(customer.isRequired()).isTrue();
                    assertThat(customer.getType()).isEqualTo(HtmlInputType.URL_VALUE);
                    assertThat(customer.getOptions()).isInstanceOfSatisfying(HalFormsOptions.Remote.class, options -> {
                        assertThat(options.getLink().getHref()).isEqualTo("/persons?page=0");
                        assertThat(options.getMinItems()).isOne();
                        assertThat(options.getMaxItems()).isOne();
                    });
                },
                products -> {
                    assertThat(products.getName()).isEqualTo("products");
                    assertThat(products.isReadOnly()).isFalse();
                    assertThat(products.isRequired()).isFalse();
                    assertThat(products.getType()).isEqualTo(HtmlInputType.URL_VALUE);
                    assertThat(products.getOptions()).isInstanceOfSatisfying(HalFormsOptions.Remote.class, options -> {
                        assertThat(options.getLink().getHref()).isEqualTo("/products?page=0");
                        assertThat(options.getMinItems()).isZero();
                        assertThat(options.getMaxItems()).isNull();
                    });
                },
                previous -> {
                    assertThat(previous.getName()).isEqualTo("previous_invoice");
                    assertThat(previous.isReadOnly()).isFalse();
                    assertThat(previous.isRequired()).isFalse();
                    assertThat(previous.getType()).isEqualTo(HtmlInputType.URL_VALUE);
                    assertThat(previous.getOptions()).isInstanceOfSatisfying(HalFormsOptions.Remote.class, options -> {
                        assertThat(options.getLink().getHref()).isEqualTo("/invoices?page=0");
                        assertThat(options.getMinItems()).isZero();
                        assertThat(options.getMaxItems()).isOne();
                    });
                },
                next -> {
                    assertThat(next.getName()).isEqualTo("next_invoice");
                    assertThat(next.isReadOnly()).isFalse();
                    assertThat(next.isRequired()).isFalse();
                    assertThat(next.getType()).isEqualTo(HtmlInputType.URL_VALUE);
                    assertThat(next.getOptions()).isInstanceOfSatisfying(HalFormsOptions.Remote.class, options -> {
                        assertThat(options.getLink().getHref()).isEqualTo("/invoices?page=0");
                        assertThat(options.getMinItems()).isZero();
                        assertThat(options.getMaxItems()).isOne();
                    });
                }
        );

        assertThat(template.getContentType()).isEqualTo(MediaType.MULTIPART_FORM_DATA_VALUE);
    }

    @Test
    void generateCreateTemplate_noContent() {
        var template = generator.generateCreateTemplate(ModelTestFixtures.APPLICATION, ModelTestFixtures.PERSON);

        assertThat(template.getProperties()).satisfiesExactlyInAnyOrder(
                name -> {
                    assertThat(name.getName()).isEqualTo("name");
                    assertThat(name.isReadOnly()).isFalse();
                    assertThat(name.isRequired()).isTrue();
                    assertThat(name.getType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                },
                vat -> {
                    assertThat(vat.getName()).isEqualTo("vat");
                    assertThat(vat.isReadOnly()).isFalse();
                    assertThat(vat.isRequired()).isTrue();
                    assertThat(vat.getType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                },
                age -> {
                    assertThat(age.getName()).isEqualTo("age");
                    assertThat(age.isReadOnly()).isFalse();
                    assertThat(age.isRequired()).isFalse();
                    assertThat(age.getType()).isEqualTo(HtmlInputType.NUMBER_VALUE);
                },
                gender -> {
                    assertThat(gender.getName()).isEqualTo("gender");
                    assertThat(gender.isReadOnly()).isFalse();
                    assertThat(gender.isRequired()).isFalse();
                    assertThat(gender.getType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                    assertThat(gender.getOptions()).isInstanceOfSatisfying(Inline.class, options -> {
                        assertThat(options.getInline()).satisfiesExactlyInAnyOrder(
                                value1 -> assertThat(value1).isEqualTo("female"),
                                value2 -> assertThat(value2).isEqualTo("male")
                        );
                        assertThat(options.getMinItems()).isZero();
                        assertThat(options.getMaxItems()).isOne();
                    });
                },
                invoices -> {
                    assertThat(invoices.getName()).isEqualTo("invoices");
                    assertThat(invoices.isReadOnly()).isFalse();
                    assertThat(invoices.isRequired()).isFalse();
                    assertThat(invoices.getType()).isEqualTo(HtmlInputType.URL_VALUE);
                    assertThat(invoices.getOptions()).isInstanceOfSatisfying(Remote.class, options -> {
                        assertThat(options.getLink().getHref()).isEqualTo("/invoices?page=0");
                        assertThat(options.getMinItems()).isZero();
                        assertThat(options.getMaxItems()).isNull();
                    });
                },
                friends -> {
                    assertThat(friends.getName()).isEqualTo("friends");
                    assertThat(friends.isReadOnly()).isFalse();
                    assertThat(friends.isRequired()).isFalse();
                    assertThat(friends.getType()).isEqualTo(HtmlInputType.URL_VALUE);
                    assertThat(friends.getOptions()).isInstanceOfSatisfying(Remote.class, options -> {
                        assertThat(options.getLink().getHref()).isEqualTo("/persons?page=0");
                        assertThat(options.getMinItems()).isZero();
                        assertThat(options.getMaxItems()).isNull();
                    });
                }
        );

        // No File input; use JSON for mediatype
        assertThat(template.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
    }

    @Test
    void generateUpdateTemplate() {
        var template = generator.generateUpdateTemplate(ModelTestFixtures.APPLICATION, ModelTestFixtures.INVOICE);

        assertThat(template.getProperties()).satisfiesExactlyInAnyOrder(
                number -> {
                    assertThat(number.getName()).isEqualTo("number");
                    assertThat(number.isReadOnly()).isFalse();
                    assertThat(number.isRequired()).isTrue();
                    assertThat(number.getType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                },
                amount -> {
                    assertThat(amount.getName()).isEqualTo("amount");
                    assertThat(amount.isReadOnly()).isFalse();
                    assertThat(amount.isRequired()).isTrue();
                    assertThat(amount.getType()).isEqualTo(HtmlInputType.NUMBER_VALUE);
                },
                received -> {
                    assertThat(received.getName()).isEqualTo("received");
                    assertThat(received.isReadOnly()).isFalse();
                    assertThat(received.isRequired()).isFalse();
                    assertThat(received.getType()).isEqualTo("datetime");
                },
                payBefore -> {
                    assertThat(payBefore.getName()).isEqualTo("pay_before");
                    assertThat(payBefore.isReadOnly()).isFalse();
                    assertThat(payBefore.isRequired()).isFalse();
                    assertThat(payBefore.getType()).isEqualTo("datetime");
                },
                paid -> {
                    assertThat(paid.getName()).isEqualTo("is_paid");
                    assertThat(paid.isReadOnly()).isFalse();
                    assertThat(paid.isRequired()).isFalse();
                    assertThat(paid.getType()).isEqualTo(HtmlInputType.CHECKBOX_VALUE);
                },
                confidentiality -> {
                    assertThat(confidentiality.getName()).isEqualTo("confidentiality");
                    assertThat(confidentiality.isReadOnly()).isFalse();
                    assertThat(confidentiality.isRequired()).isTrue();
                    assertThat(confidentiality.getType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                    assertThat(confidentiality.getOptions()).isInstanceOfSatisfying(HalFormsOptions.Inline.class, options -> {
                        assertThat(options.getInline()).satisfiesExactlyInAnyOrder(
                                value1 -> assertThat(value1).isEqualTo("public"),
                                value2 -> assertThat(value2).isEqualTo("confidential"),
                                value3 -> assertThat(value3).isEqualTo("secret")
                        );
                        assertThat(options.getMinItems()).isOne();
                        assertThat(options.getMaxItems()).isOne();
                    });
                },
                contentFilename -> {
                    assertThat(contentFilename.getName()).isEqualTo("content.filename");
                    assertThat(contentFilename.isReadOnly()).isFalse();
                    assertThat(contentFilename.isRequired()).isFalse();
                    assertThat(contentFilename.getType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                },
                contentMimetype -> {
                    assertThat(contentMimetype.getName()).isEqualTo("content.mimetype");
                    assertThat(contentMimetype.isReadOnly()).isFalse();
                    assertThat(contentMimetype.isRequired()).isFalse();
                    assertThat(contentMimetype.getType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                }
        );

        assertThat(template.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
    }

    @Test
    void generateSearchTemplate() {
        var template = generator.generateSearchTemplate(ModelTestFixtures.APPLICATION, ModelTestFixtures.INVOICE);

        assertThat(template.getProperties()).satisfiesExactlyInAnyOrder(
                number -> {
                    assertThat(number.getName()).isEqualTo("number");
                    assertThat(number.isReadOnly()).isFalse();
                    assertThat(number.isRequired()).isFalse();
                    assertThat(number.getType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                },
                confidentiality -> {
                    assertThat(confidentiality.getName()).isEqualTo("confidentiality");
                    assertThat(confidentiality.isReadOnly()).isFalse();
                    assertThat(confidentiality.isRequired()).isFalse();
                    assertThat(confidentiality.getType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                    assertThat(confidentiality.getOptions()).isInstanceOfSatisfying(HalFormsOptions.Inline.class, options -> {
                        assertThat(options.getInline()).satisfiesExactlyInAnyOrder(
                                value1 -> assertThat(value1).isEqualTo("public"),
                                value2 -> assertThat(value2).isEqualTo("confidential"),
                                value3 -> assertThat(value3).isEqualTo("secret")
                        );
                        assertThat(options.getMinItems()).isZero();
                        assertThat(options.getMaxItems()).isNull();
                    });
                },
                customerName -> {
                    assertThat(customerName.getName()).isEqualTo("customer.name~prefix");
                    assertThat(customerName.isReadOnly()).isFalse();
                    assertThat(customerName.isRequired()).isFalse();
                    assertThat(customerName.getType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                },
                customerVat -> {
                    assertThat(customerVat.getName()).isEqualTo("customer.vat");
                    assertThat(customerVat.isReadOnly()).isFalse();
                    assertThat(customerVat.isRequired()).isFalse();
                    assertThat(customerVat.getType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                },
                productsName -> {
                    assertThat(productsName.getName()).isEqualTo("products.code");
                    assertThat(productsName.isReadOnly()).isFalse();
                    assertThat(productsName.isRequired()).isFalse();
                    assertThat(productsName.getType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                },
                previousNumber -> {
                    assertThat(previousNumber.getName()).isEqualTo("previous_invoice.number");
                    assertThat(previousNumber.isReadOnly()).isFalse();
                    assertThat(previousNumber.isRequired()).isFalse();
                    assertThat(previousNumber.getType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                },
                previousConfidentiality -> {
                    assertThat(previousConfidentiality.getName()).isEqualTo("previous_invoice.confidentiality");
                    assertThat(previousConfidentiality.isReadOnly()).isFalse();
                    assertThat(previousConfidentiality.isRequired()).isFalse();
                    assertThat(previousConfidentiality.getType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                    assertThat(previousConfidentiality.getOptions()).isInstanceOfSatisfying(HalFormsOptions.Inline.class, options -> {
                        assertThat(options.getInline()).satisfiesExactlyInAnyOrder(
                                value1 -> assertThat(value1).isEqualTo("public"),
                                value2 -> assertThat(value2).isEqualTo("confidential"),
                                value3 -> assertThat(value3).isEqualTo("secret")
                        );
                        assertThat(options.getMinItems()).isZero();
                        assertThat(options.getMaxItems()).isNull();
                    });
                },
                nextNumber -> {
                    assertThat(nextNumber.getName()).isEqualTo("next_invoice.number");
                    assertThat(nextNumber.isReadOnly()).isFalse();
                    assertThat(nextNumber.isRequired()).isFalse();
                    assertThat(nextNumber.getType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                },
                nextConfidentiality -> {
                    assertThat(nextConfidentiality.getName()).isEqualTo("next_invoice.confidentiality");
                    assertThat(nextConfidentiality.isReadOnly()).isFalse();
                    assertThat(nextConfidentiality.isRequired()).isFalse();
                    assertThat(nextConfidentiality.getType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                    assertThat(nextConfidentiality.getOptions()).isInstanceOfSatisfying(HalFormsOptions.Inline.class, options -> {
                        assertThat(options.getInline()).satisfiesExactlyInAnyOrder(
                                value1 -> assertThat(value1).isEqualTo("public"),
                                value2 -> assertThat(value2).isEqualTo("confidential"),
                                value3 -> assertThat(value3).isEqualTo("secret")
                        );
                        assertThat(options.getMinItems()).isZero();
                        assertThat(options.getMaxItems()).isNull();
                    });
                },
                sort -> {
                    assertThat(sort.getName()).isEqualTo(EntityRestController.SORT_NAME);
                    assertThat(sort.isReadOnly()).isFalse();
                    assertThat(sort.isRequired()).isFalse();
                    assertThat(sort.getType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                    assertThat(sort.getOptions()).isInstanceOfSatisfying(HalFormsOptions.Inline.class, options -> {
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
                        assertThat(options.getMinItems()).isZero();
                        assertThat(options.getMaxItems()).isNull();
                    });
                }
        );

        assertThat(template.getContentType()).isNull();
    }

    @Test
    void generateSearchTemplate_toManyRelations() {
        var template = generator.generateSearchTemplate(ModelTestFixtures.APPLICATION, ModelTestFixtures.PERSON);

        assertThat(template.getProperties()).satisfiesExactlyInAnyOrder(
                name -> {
                    assertThat(name.getName()).isEqualTo("name~prefix");
                    assertThat(name.isReadOnly()).isFalse();
                    assertThat(name.isRequired()).isFalse();
                    assertThat(name.getType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                },
                vat -> {
                    assertThat(vat.getName()).isEqualTo("vat");
                    assertThat(vat.isReadOnly()).isFalse();
                    assertThat(vat.isRequired()).isFalse();
                    assertThat(vat.getType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                },
                friendsName -> {
                    assertThat(friendsName.getName()).isEqualTo("friends.name~prefix");
                    assertThat(friendsName.isReadOnly()).isFalse();
                    assertThat(friendsName.isRequired()).isFalse();
                    assertThat(friendsName.getType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                },
                friendsVat -> {
                    assertThat(friendsVat.getName()).isEqualTo("friends.vat");
                    assertThat(friendsVat.isReadOnly()).isFalse();
                    assertThat(friendsVat.isRequired()).isFalse();
                    assertThat(friendsVat.getType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                },
                invoicesNumber -> {
                    assertThat(invoicesNumber.getName()).isEqualTo("invoices.number");
                    assertThat(invoicesNumber.isReadOnly()).isFalse();
                    assertThat(invoicesNumber.isRequired()).isFalse();
                    assertThat(invoicesNumber.getType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                },
                invoicesConfidentiality -> {
                    assertThat(invoicesConfidentiality.getName()).isEqualTo("invoices.confidentiality");
                    assertThat(invoicesConfidentiality.isReadOnly()).isFalse();
                    assertThat(invoicesConfidentiality.isRequired()).isFalse();
                    assertThat(invoicesConfidentiality.getType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                    assertThat(invoicesConfidentiality.getOptions()).isInstanceOfSatisfying(HalFormsOptions.Inline.class, options -> {
                        assertThat(options.getInline()).satisfiesExactlyInAnyOrder(
                                value1 -> assertThat(value1).isEqualTo("public"),
                                value2 -> assertThat(value2).isEqualTo("confidential"),
                                value3 -> assertThat(value3).isEqualTo("secret")
                        );
                        assertThat(options.getMinItems()).isZero();
                        assertThat(options.getMaxItems()).isNull();
                    });
                },
                sort -> {
                    assertThat(sort.getName()).isEqualTo(EntityRestController.SORT_NAME);
                    assertThat(sort.isReadOnly()).isFalse();
                    assertThat(sort.isRequired()).isFalse();
                    assertThat(sort.getType()).isEqualTo(HtmlInputType.TEXT_VALUE);
                    assertThat(sort.getOptions()).isInstanceOfSatisfying(HalFormsOptions.Inline.class, options -> {
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
                        assertThat(options.getMinItems()).isZero();
                        assertThat(options.getMaxItems()).isNull();
                    });
                }
        );

        assertThat(template.getContentType()).isNull();
    }

    @Test
    void generateSearchTemplate_noSortableFields() {
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
        var template = generator.generateSearchTemplate(application, entity);

        // _sort is not present
        assertThat(template.getProperties()).singleElement().satisfies(filter -> {
            assertThat(filter.getName()).isEqualTo("one~prefix");
            assertThat(filter.getType()).isEqualTo(HtmlInputType.TEXT_VALUE);
        });
    }

    @Test
    void generateSearchTemplate_noSearchFilters() {
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
        var template = generator.generateSearchTemplate(application, entity);

        // only _sort is present
        assertThat(template.getProperties()).singleElement().satisfies(sort -> {
            assertThat(sort.getName()).isEqualTo(EntityRestController.SORT_NAME);
            assertThat(sort.getType()).isEqualTo(HtmlInputType.TEXT_VALUE);
        });
    }

    @Test
    void generateRelationTemplate_oneToOne() {
        var relationLink = "/invoices/%s/invoice-previous".formatted(ENTITY_ID);
        var templates = generator.generateRelationTemplates(ModelTestFixtures.APPLICATION, ModelTestFixtures.INVOICE_PREVIOUS, relationLink);

        assertThat(templates).satisfiesExactlyInAnyOrder(
                setPrevious -> {
                    assertThat(setPrevious.getKey()).isEqualTo("set-previous_invoice");
                    assertThat(setPrevious.getHttpMethod()).isEqualTo(HttpMethod.PUT);
                    assertThat(setPrevious.getTarget()).isEqualTo(relationLink);
                    assertThat(setPrevious.getContentType()).hasToString("text/uri-list");
                    assertThat(setPrevious.getProperties()).singleElement().satisfies(property -> {
                        assertThat(property.getName()).isEqualTo("previous_invoice");
                        assertThat(property.getType()).isEqualTo(HtmlInputType.URL_VALUE);
                        assertThat(property.getOptions()).isInstanceOfSatisfying(HalFormsOptions.Remote.class, options -> {
                            assertThat(options.getLink().getHref()).isEqualTo("/invoices?page=0");
                            assertThat(options.getMinItems()).isZero();
                            assertThat(options.getMaxItems()).isOne();
                        });
                    });
                },
                clearPrevious -> {
                    assertThat(clearPrevious.getKey()).isEqualTo("clear-previous_invoice");
                    assertThat(clearPrevious.getHttpMethod()).isEqualTo(HttpMethod.DELETE);
                    assertThat(clearPrevious.getTarget()).isEqualTo(relationLink);
                    assertThat(clearPrevious.getProperties()).isEmpty();
                }
        );
    }

    @Test
    void generateRelationTemplate_manyToOne() {
        var relationLink = "/invoices/%s/customer".formatted(ENTITY_ID);
        var templates = generator.generateRelationTemplates(ModelTestFixtures.APPLICATION, ModelTestFixtures.INVOICE_CUSTOMER, relationLink);

        assertThat(templates).satisfiesExactlyInAnyOrder(
                setCustomer -> {
                    assertThat(setCustomer.getKey()).isEqualTo("set-customer");
                    assertThat(setCustomer.getHttpMethod()).isEqualTo(HttpMethod.PUT);
                    assertThat(setCustomer.getTarget()).isEqualTo(relationLink);
                    assertThat(setCustomer.getContentType()).hasToString("text/uri-list");
                    assertThat(setCustomer.getProperties()).singleElement().satisfies(property -> {
                        assertThat(property.getName()).isEqualTo("customer");
                        assertThat(property.getType()).isEqualTo(HtmlInputType.URL_VALUE);
                        assertThat(property.isRequired()).isTrue();
                        assertThat(property.getOptions()).isInstanceOfSatisfying(HalFormsOptions.Remote.class, options -> {
                            assertThat(options.getLink().getHref()).isEqualTo("/persons?page=0");
                            assertThat(options.getMinItems()).isOne();
                            assertThat(options.getMaxItems()).isOne();
                        });
                    });
                }
        );
    }

    @Test
    void generateRelationTemplate_oneToMany() {
        var relationLink = "/persons/%s/invoices".formatted(ENTITY_ID);
        var templates = generator.generateRelationTemplates(ModelTestFixtures.APPLICATION, ModelTestFixtures.PERSON_INVOICES, relationLink);

        assertThat(templates).satisfiesExactlyInAnyOrder(
                addInvoices -> {
                    assertThat(addInvoices.getKey()).isEqualTo("add-invoices");
                    assertThat(addInvoices.getHttpMethod()).isEqualTo(HttpMethod.POST);
                    assertThat(addInvoices.getTarget()).isEqualTo(relationLink);
                    assertThat(addInvoices.getContentType()).hasToString("text/uri-list");
                    assertThat(addInvoices.getProperties()).singleElement().satisfies(property -> {
                        assertThat(property.getName()).isEqualTo("invoices");
                        assertThat(property.getType()).isEqualTo(HtmlInputType.URL_VALUE);
                        assertThat(property.getOptions()).isInstanceOfSatisfying(HalFormsOptions.Remote.class, options -> {
                            assertThat(options.getLink().getHref()).isEqualTo("/invoices?page=0");
                            assertThat(options.getMinItems()).isZero();
                            assertThat(options.getMaxItems()).isNull();
                        });
                    });
                }
        );
    }

    @Test
    void generateRelationTemplate_manyToMany() {
        var relationLink = "/invoices/%s/products".formatted(ENTITY_ID);
        var templates = generator.generateRelationTemplates(ModelTestFixtures.APPLICATION, ModelTestFixtures.INVOICE_PRODUCTS, relationLink);

        assertThat(templates).satisfiesExactlyInAnyOrder(
                addProducts -> {
                    assertThat(addProducts.getKey()).isEqualTo("add-products");
                    assertThat(addProducts.getHttpMethod()).isEqualTo(HttpMethod.POST);
                    assertThat(addProducts.getTarget()).isEqualTo(relationLink);
                    assertThat(addProducts.getContentType()).hasToString("text/uri-list");
                    assertThat(addProducts.getProperties()).singleElement().satisfies(property -> {
                        assertThat(property.getName()).isEqualTo("products");
                        assertThat(property.getType()).isEqualTo(HtmlInputType.URL_VALUE);
                        assertThat(property.getOptions()).isInstanceOfSatisfying(HalFormsOptions.Remote.class, options -> {
                            assertThat(options.getLink().getHref()).isEqualTo("/products?page=0");
                            assertThat(options.getMinItems()).isZero();
                            assertThat(options.getMaxItems()).isNull();
                        });
                    });
                },
                clearProducts -> {
                    assertThat(clearProducts.getKey()).isEqualTo("clear-products");
                    assertThat(clearProducts.getHttpMethod()).isEqualTo(HttpMethod.DELETE);
                    assertThat(clearProducts.getTarget()).isEqualTo(relationLink);
                    assertThat(clearProducts.getProperties()).isEmpty();
                }
        );
    }

    @Test
    void generateContentTemplates() {
        var contentLink = "/invoices/%s/content";
        var templates = generator.generateContentTemplates(ModelTestFixtures.APPLICATION, ModelTestFixtures.INVOICE, ModelTestFixtures.INVOICE_CONTENT, contentLink);

        assertThat(templates).isEmpty();
    }
}