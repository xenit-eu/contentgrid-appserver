package com.contentgrid.appserver;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.CompositeAttributeImpl;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint;
import com.contentgrid.appserver.application.model.searchfilters.ExactSearchFilter;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.domain.DatamodelApi;
import com.contentgrid.appserver.domain.DatamodelApiImpl;
import com.contentgrid.appserver.query.engine.api.QueryEngine;
import com.contentgrid.appserver.query.engine.api.TableCreator;
import com.contentgrid.appserver.query.engine.jooq.JOOQQueryEngine;
import com.contentgrid.appserver.query.engine.jooq.JOOQTableCreator;
import com.contentgrid.appserver.query.engine.jooq.resolver.AutowiredDSLContextResolver;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;
import com.contentgrid.appserver.registry.ApplicationResolver;
import com.contentgrid.appserver.registry.SingleApplicationResolver;
import com.contentgrid.appserver.rest.ArgumentResolverConfigurer;
import com.contentgrid.appserver.rest.links.ContentGridLinksConfiguration;
import com.contentgrid.appserver.rest.problem.ContentgridProblemDetailConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.hateoas.config.EnableHypermediaSupport;
import org.springframework.hateoas.config.EnableHypermediaSupport.HypermediaType;

@Slf4j
@Configuration
@EnableHypermediaSupport(type = { HypermediaType.HAL, HypermediaType.HAL_FORMS })
@Import({ContentgridProblemDetailConfiguration.class, ArgumentResolverConfigurer.class, ContentGridLinksConfiguration.class})
public class ContentgridAppConfiguration {

    @Bean
    public DatamodelApi api(QueryEngine queryEngine) {
        return new DatamodelApiImpl(queryEngine);
    }

    @Bean
    public DSLContextResolver autowiredDSLContextResolver(DSLContext dslContext) {
        return new AutowiredDSLContextResolver(dslContext);
    }

    @Bean
    public TableCreator jooqTableCreator(DSLContextResolver dslContextResolver) {
        return new JOOQTableCreator(dslContextResolver);
    }

    @Bean
    public QueryEngine jooqQueryEngine(DSLContextResolver dslContextResolver) {
        return new JOOQQueryEngine(dslContextResolver);
    }

    @Bean
    ApplicationResolver applicationResolver() {
        var person = Entity.builder()
                .name(EntityName.of("person"))
                .table(TableName.of("person"))
                .pathSegment(PathSegmentName.of("persons"))
                .linkName(LinkName.of("persons"))
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
                        .attributeType(Type.TEXT)
                        .build())
                .searchFilter(ExactSearchFilter.builder()
                        .name(FilterName.of("last_name"))
                        .attributePath(PropertyPath.of(AttributeName.of("last_name")))
                        .attributeType(Type.TEXT)
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
                        .attributeType(Type.TEXT)
                        .build())
                .searchFilter(ExactSearchFilter.builder()
                        .name(FilterName.of("address.country"))
                        .attributePath(PropertyPath.of(AttributeName.of("address"), AttributeName.of("country")))
                        .attributeType(Type.TEXT)
                        .build())
                .searchFilter(ExactSearchFilter.builder()
                        .name(FilterName.of("address.residence.street"))
                        .attributePath(PropertyPath.of(AttributeName.of("address"), AttributeName.of("residence"), AttributeName.of("street")))
                        .attributeType(Type.TEXT)
                        .build())
                .searchFilter(ExactSearchFilter.builder()
                        .name(FilterName.of("address.residence.number"))
                        .attributePath(PropertyPath.of(AttributeName.of("address"), AttributeName.of("residence"), AttributeName.of("number")))
                        .attributeType(Type.TEXT)
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
                        .attributeType(Type.TEXT)
                        .build())
                .searchFilter(ExactSearchFilter.builder()
                        .name(FilterName.of("amount"))
                        .attributePath(PropertyPath.of(AttributeName.of("amount")))
                        .attributeType(Type.DOUBLE)
                        .build())
                .build();
        var shipmentToInvoice = ManyToOneRelation.builder()
                .sourceEndPoint(RelationEndPoint.builder()
                        .entity(shipment)
                        .name(RelationName.of("invoice"))
                        .pathSegment(PathSegmentName.of("invoice"))
                        .linkName(LinkName.of("invoice"))
                        .build())
                .targetEndPoint(RelationEndPoint.builder()
                        .entity(invoice)
                        .name(RelationName.of("shipments"))
                        .pathSegment(PathSegmentName.of("shipments"))
                        .linkName(LinkName.of("shipments"))
                        .build())
                .targetReference(ColumnName.of("invoice"))
                .build();
        return new SingleApplicationResolver(
                Application.builder()
                        .name(ApplicationName.of("test"))
                        .entity(person)
                        .entity(shipment)
                        .entity(invoice)
                        .relation(shipmentToInvoice)
                        .build()
                        .withPropagatedSearchFilters()
        );
    }
}
