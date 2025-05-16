package com.contentgrid.appserver;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.query.engine.api.QueryEngine;
import com.contentgrid.appserver.query.engine.api.TableCreator;
import com.contentgrid.appserver.query.engine.jooq.JOOQQueryEngine;
import com.contentgrid.appserver.query.engine.jooq.JOOQTableCreator;
import com.contentgrid.appserver.query.engine.jooq.resolver.AutowiredDSLContextResolver;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;
import com.contentgrid.appserver.registry.ApplicationResolver;
import com.contentgrid.appserver.registry.SingleApplicationResolver;
import com.contentgrid.appserver.rest.ArgumentResolverConfigurer;
import com.contentgrid.appserver.rest.assembler.EntityDataRepresentationModelAssembler;
import com.contentgrid.appserver.rest.assembler.EntityDataRepresentationModelAssemblerProvider;
import com.contentgrid.appserver.rest.problem.ContentgridProblemDetailConfiguration;
import java.util.HashMap;
import java.util.Map;
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
@Import({ContentgridProblemDetailConfiguration.class, ArgumentResolverConfigurer.class})
public class ContentgridAppConfiguration {

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
        return new SingleApplicationResolver(
                Application.builder()
                        .name(ApplicationName.of("test"))
                        .entity(Entity.builder()
                                .name(EntityName.of("person"))
                                .table(TableName.of("person"))
                                .pathSegment(PathSegmentName.of("persons"))
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
                                .build())
                        .entity(Entity.builder()
                                .name(EntityName.of("invoice"))
                                .table(TableName.of("invoice"))
                                .pathSegment(PathSegmentName.of("invoices"))
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
                                        .idColumn(ColumnName.of("content__id"))
                                        .filenameColumn(ColumnName.of("content__filename"))
                                        .mimetypeColumn(ColumnName.of("content__mimetype"))
                                        .lengthColumn(ColumnName.of("content__length"))
                                        .build())
                                .build())
                        .build()
        );
    }

    @Bean
    EntityDataRepresentationModelAssemblerProvider entityDataRepresentationModelAssemblerProvider() {
        return new EntityDataRepresentationModelAssemblerProvider() {
            final Map<ApplicationName, EntityDataRepresentationModelAssembler> assemblers = new HashMap<>();
            @Override
            public EntityDataRepresentationModelAssembler getAssemblerFor(Application application) {
                return assemblers.computeIfAbsent(application.getName(),
                        (_a) -> new EntityDataRepresentationModelAssembler(application));
            }
        };
    }
}
