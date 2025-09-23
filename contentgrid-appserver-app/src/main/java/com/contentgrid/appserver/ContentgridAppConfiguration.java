package com.contentgrid.appserver;

import com.contentgrid.appserver.actuator.ActuatorConfiguration;
import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.CompositeAttributeImpl;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.attributes.flags.ETagFlag;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint;
import com.contentgrid.appserver.application.model.relations.flags.HiddenEndpointFlag;
import com.contentgrid.appserver.application.model.searchfilters.ExactSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.flags.HiddenSearchFilterFlag;
import com.contentgrid.appserver.application.model.sortable.SortableField;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.SortableName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.registry.ApplicationResolver;
import com.contentgrid.appserver.registry.SingleApplicationResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ContentgridAppConfiguration {

    @Bean
    ApplicationResolver applicationResolver() {
        var person = Entity.builder()
                .name(EntityName.of("person"))
                .table(TableName.of("person"))
                .pathSegment(PathSegmentName.of("persons"))
                .linkName(LinkName.of("persons"))
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("_version"))
                        .column(ColumnName.of("_version"))
                        .type(Type.LONG)
                        .flag(ETagFlag.INSTANCE)
                        .build()
                )
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("first_name"))
                        .description("First name")
                        .column(ColumnName.of("first_name"))
                        .type(Type.TEXT)
                        .build()
                )
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("last_name"))
                        .description("Last name")
                        .column(ColumnName.of("last_name"))
                        .type(Type.TEXT)
                        .build()
                )
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("birth_date"))
                        .description("Birth date")
                        .column(ColumnName.of("birth_date"))
                        .type(Type.DATETIME)
                        .build()
                )
                .searchFilter(ExactSearchFilter.builder()
                        .name(FilterName.of("first_name"))
                        .attributePath(PropertyPath.of(AttributeName.of("first_name")))
                        .build())
                .searchFilter(ExactSearchFilter.builder()
                        .name(FilterName.of("last_name"))
                        .attributePath(PropertyPath.of(AttributeName.of("last_name")))
                        .build())
                .searchFilter(ExactSearchFilter.builder()
                        .name(FilterName.of("_internal_person__friends"))
                        .attributePath(PropertyPath.of(RelationName.of("__inverse_friends"), AttributeName.of("id")))
                        .flag(HiddenSearchFilterFlag.INSTANCE)
                        .build())
                .sortableField(SortableField.builder()
                        .name(SortableName.of("first_name"))
                        .propertyPath(PropertyPath.of(AttributeName.of("first_name")))
                        .build())
                .sortableField(SortableField.builder()
                        .name(SortableName.of("last_name"))
                        .propertyPath(PropertyPath.of(AttributeName.of("last_name")))
                        .build())
                .build();
        var shipment = Entity.builder()
                .name(EntityName.of("shipment"))
                .table(TableName.of("shipment"))
                .pathSegment(PathSegmentName.of("shipments"))
                .linkName(LinkName.of("shipments"))
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("shipped_date"))
                        .column(ColumnName.of("shipped_date"))
                        .type(Type.DATETIME)
                        .build())
                .attribute(CompositeAttributeImpl.builder() // Note: POSTing composite attributes is not possible
                        .name(AttributeName.of("address"))
                        .attribute(SimpleAttribute.builder()
                                .name(AttributeName.of("city"))
                                .column(ColumnName.of("address__city"))
                                .type(Type.TEXT)
                                .build())
                        .attribute(SimpleAttribute.builder()
                                .name(AttributeName.of("country"))
                                .column(ColumnName.of("address__country"))
                                .type(Type.TEXT)
                                .build())
                        .attribute(CompositeAttributeImpl.builder()
                                .name(AttributeName.of("residence"))
                                .attribute(SimpleAttribute.builder()
                                        .name(AttributeName.of("street"))
                                        .column(ColumnName.of("address__residence__street"))
                                        .type(Type.TEXT)
                                        .build())
                                .attribute(SimpleAttribute.builder()
                                        .name(AttributeName.of("number"))
                                        .column(ColumnName.of("address__residence__number"))
                                        .type(Type.TEXT)
                                        .build())
                                .build())
                        .build())
                .searchFilter(ExactSearchFilter.builder()
                        .name(FilterName.of("address.city"))
                        .attributePath(PropertyPath.of(AttributeName.of("address"), AttributeName.of("city")))
                        .build())
                .searchFilter(ExactSearchFilter.builder()
                        .name(FilterName.of("address.country"))
                        .attributePath(PropertyPath.of(AttributeName.of("address"), AttributeName.of("country")))
                        .build())
                .searchFilter(ExactSearchFilter.builder()
                        .name(FilterName.of("address.residence.street"))
                        .attributePath(PropertyPath.of(AttributeName.of("address"), AttributeName.of("residence"), AttributeName.of("street")))
                        .build())
                .searchFilter(ExactSearchFilter.builder()
                        .name(FilterName.of("address.residence.number"))
                        .attributePath(PropertyPath.of(AttributeName.of("address"), AttributeName.of("residence"), AttributeName.of("number")))
                        .build())
                .searchFilter(ExactSearchFilter.builder()
                        .name(FilterName.of("invoice.number"))
                        .attributePath(PropertyPath.of(RelationName.of("invoice"), AttributeName.of("number")))
                        .build())
                .build();
        var invoice = Entity.builder()
                .name(EntityName.of("invoice"))
                .table(TableName.of("invoice"))
                .pathSegment(PathSegmentName.of("invoices"))
                .linkName(LinkName.of("invoices"))
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("number"))
                        .column(ColumnName.of("number"))
                        .type(Type.TEXT)
                        .constraint(Constraint.required())
                        .constraint(Constraint.unique())
                        .build()
                )
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("amount"))
                        .column(ColumnName.of("amount"))
                        .type(Type.DOUBLE)
                        .constraint(Constraint.required())
                        .build()
                )
                .attribute(ContentAttribute.builder()
                        .name(AttributeName.of("content"))
                        .pathSegment(PathSegmentName.of("content"))
                        .linkName(LinkName.of("content"))
                        .idColumn(ColumnName.of("content__id"))
                        .filenameColumn(ColumnName.of("content__filename"))
                        .mimetypeColumn(ColumnName.of("content__mimetype"))
                        .lengthColumn(ColumnName.of("content__length"))
                        .build())
                .searchFilter(ExactSearchFilter.builder()
                        .name(FilterName.of("number"))
                        .attributePath(PropertyPath.of(AttributeName.of("number")))
                        .build())
                .searchFilter(ExactSearchFilter.builder()
                        .name(FilterName.of("amount"))
                        .attributePath(PropertyPath.of(AttributeName.of("amount")))
                        .build())
                .searchFilter(ExactSearchFilter.builder()
                        .name(FilterName.of("shipments.address.country"))
                        .attributePath(PropertyPath.of(RelationName.of("shipments"), AttributeName.of("address"), AttributeName.of("country")))
                        .build())
                .sortableField(SortableField.builder()
                        .name(SortableName.of("number"))
                        .propertyPath(PropertyPath.of(AttributeName.of("number")))
                        .build())
                .sortableField(SortableField.builder()
                        .name(SortableName.of("amount"))
                        .propertyPath(PropertyPath.of(AttributeName.of("amount")))
                        .build())
                .build();
        var shipmentToInvoice = ManyToOneRelation.builder()
                .sourceEndPoint(RelationEndPoint.builder()
                        .entity(shipment.getName())
                        .name(RelationName.of("invoice"))
                        .pathSegment(PathSegmentName.of("invoice"))
                        .linkName(LinkName.of("invoice"))
                        .build())
                .targetEndPoint(RelationEndPoint.builder()
                        .entity(invoice.getName())
                        .name(RelationName.of("shipments"))
                        .pathSegment(PathSegmentName.of("shipments"))
                        .linkName(LinkName.of("shipments"))
                        .build())
                .targetReference(ColumnName.of("invoice"))
                .build();
        var customerToInvoice = OneToManyRelation.builder()
                .sourceEndPoint(RelationEndPoint.builder()
                        .entity(person.getName())
                        .name(RelationName.of("invoices"))
                        .pathSegment(PathSegmentName.of("invoices"))
                        .linkName(LinkName.of("invoices"))
                        .build())
                .targetEndPoint(RelationEndPoint.builder()
                        .entity(invoice.getName())
                        .name(RelationName.of("customer"))
                        .pathSegment(PathSegmentName.of("customer"))
                        .linkName(LinkName.of("customer"))
                        .build())
                .sourceReference(ColumnName.of("customer"))
                .build();

        var personFriends = ManyToManyRelation.builder()
                .sourceEndPoint(
                        RelationEndPoint.builder()
                                .name(RelationName.of("friends"))
                                .entity(person.getName())
                                .pathSegment(PathSegmentName.of("friends"))
                                .linkName(LinkName.of("friends"))
                                .build()
                )
                .targetEndPoint(
                        RelationEndPoint.builder()
                                .name(RelationName.of("__inverse_friends"))
                                .entity(person.getName())
                                .flag(HiddenEndpointFlag.INSTANCE)
                                .build()
                )
                .joinTable(TableName.of("person__friends"))
                .sourceReference(ColumnName.of("person_src_id"))
                .targetReference(ColumnName.of("person_tgt_id"))
                .build();


        return new SingleApplicationResolver(
                Application.builder()
                        .name(ApplicationName.of("test"))
                        .entity(person)
                        .entity(shipment)
                        .entity(invoice)
                        .relation(shipmentToInvoice)
                        .relation(customerToInvoice)
                        .relation(personFriends)
                        .build()
        );
    }
}
