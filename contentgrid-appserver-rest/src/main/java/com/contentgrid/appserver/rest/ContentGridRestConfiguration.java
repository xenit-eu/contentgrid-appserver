package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.i18n.UserLocales;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.domain.LinkUriProvider;
import com.contentgrid.appserver.domain.data.EntityInstance;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.registry.DefaultApplicationNameExtractor;
import com.contentgrid.appserver.rest.automations.ContentGridAutomationsConfiguration;
import com.contentgrid.appserver.rest.converter.RequestInputDataJacksonModule;
import com.contentgrid.appserver.rest.converter.UriListHttpMessageConverter;
import com.contentgrid.appserver.rest.data.conversion.LongDataEntryToDecimalDataEntryConverter;
import com.contentgrid.appserver.rest.data.conversion.StringDataEntryToBooleanDataEntryConverter;
import com.contentgrid.appserver.rest.data.conversion.StringDataEntryToDecimalDataEntryConverter;
import com.contentgrid.appserver.rest.data.conversion.StringDataEntryToInstantDataEntryConverter;
import com.contentgrid.appserver.rest.data.conversion.StringDataEntryToLocalDateDataEntryConverter;
import com.contentgrid.appserver.rest.data.conversion.StringDataEntryToLongDataEntryConverter;
import com.contentgrid.appserver.rest.entity.ContentRestController;
import com.contentgrid.appserver.rest.entity.EntityRestController;
import com.contentgrid.appserver.rest.entity.XToManyRelationRestController;
import com.contentgrid.appserver.rest.entity.XToOneRelationRestController;
import com.contentgrid.appserver.rest.entity.assembler.EntityDataRepresentationModelAssembler;
import com.contentgrid.appserver.rest.filter.SingleRangeRequestServletFilter;
import com.contentgrid.appserver.rest.hal.forms.HalFormsMediaTypeConfiguration;
import com.contentgrid.appserver.rest.hal.links.ContentGridLinksConfiguration;
import com.contentgrid.appserver.rest.hal.links.DomainLinkUriProvider;
import com.contentgrid.appserver.rest.hal.links.factory.LinkFactoryProvider;
import com.contentgrid.appserver.rest.mapping.ContentGridHandlerMappingConfiguration;
import com.contentgrid.appserver.rest.metadata.RootRestController;
import com.contentgrid.appserver.rest.problem.ContentgridProblemDetailConfiguration;
import com.contentgrid.appserver.rest.profile.ProfileRestController;
import com.contentgrid.appserver.rest.profile.assembler.BlueprintLinkRelationsConfiguration;
import com.contentgrid.appserver.rest.profile.assembler.hal.ProfileEntityRepresentationModelAssembler;
import com.contentgrid.hateoas.spring.pagination.PaginationHandlerMethodArgumentResolver;
import com.contentgrid.hateoas.spring.pagination.SlicedResourcesAssembler;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.format.Formatter;
import org.springframework.format.FormatterRegistry;
import org.springframework.hateoas.config.EnableHypermediaSupport;
import org.springframework.hateoas.config.EnableHypermediaSupport.HypermediaType;
import org.springframework.hateoas.mediatype.MediaTypeConfigurationCustomizer;
import org.springframework.hateoas.mediatype.hal.HalConfiguration;
import org.springframework.hateoas.server.MethodLinkBuilderFactory;
import org.springframework.http.MediaType;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@EnableHypermediaSupport(type = { HypermediaType.HAL })
@Import({
        ContentgridProblemDetailConfiguration.class,
        ContentGridLinksConfiguration.class,
        BlueprintLinkRelationsConfiguration.class,
        HalFormsMediaTypeConfiguration.class,
        ContentGridHandlerMappingConfiguration.class,
        ContentRestController.class,
        DefaultApplicationNameExtractor.class,
        EntityDataRepresentationModelAssembler.class,
        EntityRestController.class,
        ProfileEntityRepresentationModelAssembler.class,
        ProfileRestController.class,
        RequestInputDataJacksonModule.class,
        RootRestController.class,
        SingleRangeRequestServletFilter.class,
        UriListHttpMessageConverter.class,
        VersionValidator.class,
        XToManyRelationRestController.class,
        XToOneRelationRestController.class,
        ApplicationArgumentResolver.class,
        AuthorizationContextArgumentResolver.class,
        EncodedCursorPaginationHandlerMethodArgumentResolver.class,
        VersionConstraintArgumentResolver.class,
        LinkProviderArgumentResolver.class,
        UserLocalesArgumentResolver.class,
        ContentGridAutomationsConfiguration.class,
})
public class ContentGridRestConfiguration {

    @Bean
    WebMvcConfigurer contentgridRestWebmvcConfigurer(List<HandlerMethodArgumentResolver> customResolvers) {
        return new WebMvcConfigurer() {

            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.addAll(customResolvers);
            }

            @Override
            public void addFormatters(FormatterRegistry registry) {
                registry.addConverter(new StringDataEntryToBooleanDataEntryConverter());
                registry.addConverter(new StringDataEntryToDecimalDataEntryConverter());
                registry.addConverter(new StringDataEntryToInstantDataEntryConverter());
                registry.addConverter(new StringDataEntryToLocalDateDataEntryConverter());
                registry.addConverter(new StringDataEntryToLongDataEntryConverter());
                registry.addConverter(new LongDataEntryToDecimalDataEntryConverter());
                registry.addFormatter(new Formatter<EntityId>() {
                    @Override
                    public EntityId parse(String text, Locale locale) throws ParseException {
                        return EntityId.of(UUID.fromString(text));
                    }

                    @Override
                    public String print(EntityId entityId, Locale locale) {
                        return entityId.getValue().toString();
                    }
                });
                registry.addConverter(new VersionConstraintArgumentResolver());
            }
        };
    }

    @Bean
    SlicedResourcesAssembler<EntityInstance> slicedResourcesAssembler(PaginationHandlerMethodArgumentResolver resolver) {
        return new SlicedResourcesAssembler<>(resolver);
    }

    /**
     * Serves HAL to clients requesting plain {@code application/json}.
     * <p>
     * Spring Boot only configures this when its {@code spring-boot-hateoas} autoconfiguration is present;
     * we use {@link EnableHypermediaSupport} directly, so it has to be configured here.
     */
    @Bean
    MediaTypeConfigurationCustomizer<HalConfiguration> applicationJsonHalConfigurationCustomizer() {
        return halConfiguration -> halConfiguration.withMediaType(MediaType.APPLICATION_JSON);
    }

    @Bean
    Function<Application, LinkUriProvider> defaultLinkUriProviderFactory(MethodLinkBuilderFactory<?> linkBuilderFactory) {
        return application -> new DomainLinkUriProvider(new LinkFactoryProvider(
                application,
                UserLocales.defaults(),
                linkBuilderFactory
        ));
    }
}
