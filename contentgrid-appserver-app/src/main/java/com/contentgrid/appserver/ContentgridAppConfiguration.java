package com.contentgrid.appserver;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.query.DummyQueryEngine;
import com.contentgrid.appserver.query.QueryEngine;
import com.contentgrid.appserver.rest.exception.ContentGridExceptionHandler;
import com.contentgrid.appserver.rest.problem.ContentgridProblemDetailConfiguration;
import com.contentgrid.appserver.rest.problem.ProblemFactory;
import com.contentgrid.appserver.rest.problem.ProblemTypeMessageSource;
import com.contentgrid.appserver.rest.problem.ProblemTypeUrlFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.core.annotation.Order;
import org.springframework.hateoas.UriTemplate;
import org.springframework.hateoas.config.EnableHypermediaSupport;
import org.springframework.hateoas.config.EnableHypermediaSupport.HypermediaType;

@Slf4j
@Configuration
@EnableHypermediaSupport(type = { HypermediaType.HAL, HypermediaType.HAL_FORMS })
@Import({ContentgridProblemDetailConfiguration.class})
public class ContentgridAppConfiguration {

    @Bean
    QueryEngine queryEngine() {
        return new DummyQueryEngine();
    }

    @Bean
    Application application() {
        return Application.builder()
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
                .build();
    }
}
